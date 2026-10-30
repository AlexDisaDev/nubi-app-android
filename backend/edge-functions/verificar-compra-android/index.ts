// verificar-compra-android
//
// Verifica una compra de suscripción de Android (Google Play Billing)
// contra la Google Play Developer API y, si es válida, activa el premium
// en Supabase. La llama Compras.kt justo después de que Play confirme la
// compra (ver PurchasesUpdatedListener / procesar() en el proyecto
// Android). Nunca confía en nada que mande el cliente sobre el estado de
// la compra: vuelve a consultarlo a Google con credenciales propias.
//
// Variables de entorno necesarias:
//   GOOGLE_SERVICE_ACCOUNT_JSON — el JSON completo de la cuenta de
//     servicio de Google Cloud con acceso a la Google Play Developer API
//     (ver backend/README.md para cómo crearla y dársela de alta en
//     Play Console). Se configura como "Edge Function secret" en el
//     panel de Supabase o con `supabase secrets set`.
//   SUPABASE_URL, SUPABASE_ANON_KEY y SUPABASE_SERVICE_ROLE_KEY — los
//     pone Supabase solo en cada Edge Function, no hace falta tocarlos.
//
// Request esperado:
//   POST, con el access_token de la sesión del usuario en la cabecera
//   "Authorization: Bearer <access_token>" (lo pone solo el cliente
//   Supabase de Kotlin si se usa functions.invoke con sesión iniciada).
//   Body JSON: { "purchaseToken": string, "productId": string,
//                "basePlanId": string | null }
//
// Respuesta: { ok, estado, premiumHasta, tieneAcceso } o { error }.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const PAQUETE_ANDROID = "com.app.nubi";
const SCOPE_ANDROID_PUBLISHER = "https://www.googleapis.com/auth/androidpublisher";

// Mapa de subscriptionState (Google) -> estado que guardamos nosotros.
// Valores de Google confirmados en su documentación de SubscriptionPurchaseV2.
const MAPA_ESTADOS: Record<string, string> = {
  SUBSCRIPTION_STATE_ACTIVE: "activa",
  SUBSCRIPTION_STATE_IN_GRACE_PERIOD: "en_periodo_de_gracia",
  SUBSCRIPTION_STATE_CANCELED: "cancelada_con_acceso",
  SUBSCRIPTION_STATE_PAUSED: "pausada",
  SUBSCRIPTION_STATE_ON_HOLD: "en_espera",
  SUBSCRIPTION_STATE_PENDING: "pendiente",
  SUBSCRIPTION_STATE_EXPIRED: "expirada",
  SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED: "expirada",
};

const ESTADOS_CON_ACCESO = new Set(["activa", "en_periodo_de_gracia", "cancelada_con_acceso"]);

function base64UrlDesdeBytes(bytes: Uint8Array): string {
  let binario = "";
  for (const b of bytes) binario += String.fromCharCode(b);
  return btoa(binario).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlDesdeTexto(texto: string): string {
  return base64UrlDesdeBytes(new TextEncoder().encode(texto));
}

/** El private_key de la cuenta de servicio viene en formato PEM; hay que
 *  convertirlo a una CryptoKey para poder firmar con Web Crypto. */
async function pemAClavePrivada(pem: string): Promise<CryptoKey> {
  const cuerpo = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binario = atob(cuerpo);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i++) bytes[i] = binario.charCodeAt(i);
  return crypto.subtle.importKey(
    "pkcs8",
    bytes.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/** Flujo estándar de OAuth2 "JWT bearer" para cuentas de servicio de
 *  Google: se firma un JWT propio y se cambia por un access_token. */
async function obtenerTokenDeAcceso(
  cuentaServicio: { client_email: string; private_key: string },
): Promise<string> {
  const ahora = Math.floor(Date.now() / 1000);
  const cabecera = base64UrlDesdeTexto(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const reclamaciones = base64UrlDesdeTexto(JSON.stringify({
    iss: cuentaServicio.client_email,
    scope: SCOPE_ANDROID_PUBLISHER,
    aud: "https://oauth2.googleapis.com/token",
    iat: ahora,
    exp: ahora + 3600,
  }));
  const sinFirmar = `${cabecera}.${reclamaciones}`;

  const clave = await pemAClavePrivada(cuentaServicio.private_key);
  const firma = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    clave,
    new TextEncoder().encode(sinFirmar),
  );
  const jwt = `${sinFirmar}.${base64UrlDesdeBytes(new Uint8Array(firma))}`;

  const respuesta = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!respuesta.ok) {
    throw new Error(`No se pudo obtener el token de Google: ${await respuesta.text()}`);
  }
  const datos = await respuesta.json();
  return datos.access_token as string;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "metodo_no_permitido" }), { status: 405 });
  }

  try {
    const cabeceraAuth = req.headers.get("Authorization") ?? "";
    if (!cabeceraAuth.startsWith("Bearer ")) {
      return new Response(JSON.stringify({ error: "sin_sesion" }), { status: 401 });
    }

    // Cliente "como el usuario" SOLO para averiguar quién es de verdad —
    // nunca se confía en un usuario_id que mande el propio cliente, eso
    // dejaría a cualquiera activarse premium a sí mismo con solo cambiar
    // el id en el body.
    const supabaseComoUsuario = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: cabeceraAuth } } },
    );
    const { data: datosUsuario, error: errorUsuario } = await supabaseComoUsuario.auth.getUser();
    if (errorUsuario || !datosUsuario?.user) {
      return new Response(JSON.stringify({ error: "sesion_invalida" }), { status: 401 });
    }
    const usuarioId = datosUsuario.user.id;

    const cuerpo = await req.json();
    const purchaseToken = cuerpo.purchaseToken as string | undefined;
    const productId = cuerpo.productId as string | undefined;
    const basePlanIdCliente = (cuerpo.basePlanId as string | undefined) ?? null;
    if (!purchaseToken || !productId) {
      return new Response(JSON.stringify({ error: "faltan_datos" }), { status: 400 });
    }

    const cuentaServicioJson = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON");
    if (!cuentaServicioJson) {
      throw new Error("Falta el secreto GOOGLE_SERVICE_ACCOUNT_JSON");
    }
    const cuentaServicio = JSON.parse(cuentaServicioJson);
    const tokenAcceso = await obtenerTokenDeAcceso(cuentaServicio);

    // 1) Consultar el estado real de la suscripción en Google — la única
    // fuente de verdad, nunca lo que diga el móvil.
    const urlConsulta =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PAQUETE_ANDROID}` +
      `/purchases/subscriptionsv2/tokens/${purchaseToken}`;
    const respuestaGoogle = await fetch(urlConsulta, {
      headers: { Authorization: `Bearer ${tokenAcceso}` },
    });
    if (!respuestaGoogle.ok) {
      const texto = await respuestaGoogle.text();
      return new Response(
        JSON.stringify({ error: "google_rechazo_la_consulta", detalle: texto }),
        { status: 502 },
      );
    }
    const compra = await respuestaGoogle.json();

    const estado = MAPA_ESTADOS[compra.subscriptionState as string] ?? "pendiente";
    const lineItem = (compra.lineItems ?? [])[0];
    const expiraEn: string | null = lineItem?.expiryTime ?? null;
    // El plan base real lo dice Google, no el cliente — el valor que
    // manda el móvil es solo un respaldo por si este campo faltara.
    const planBaseId: string | null = lineItem?.offerDetails?.basePlanId ?? basePlanIdCliente;
    const reconocidaYa = compra.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED";

    // 2) Guardar/activar con la service_role key (bypassa RLS a
    // propósito: esta es la única vía fiable, porque ya se verificó
    // contra Google en el paso anterior).
    const supabaseAdmin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    const { error: errorRpc } = await supabaseAdmin.rpc("activar_premium_por_compra_google", {
      p_usuario_id: usuarioId,
      p_purchase_token: purchaseToken,
      p_producto_id: productId,
      p_plan_base_id: planBaseId,
      p_estado: estado,
      p_expira_en: expiraEn,
    });
    if (errorRpc) throw errorRpc;

    // 3) Reconocer la compra ante Google si hace falta. Se intenta aquí,
    // en servidor (recomendado por Google si hay backend propio), además
    // del acknowledge que ya hace el móvil por su cuenta como red de
    // seguridad — si uno de los dos falla, el otro cubre el plazo de 3
    // días antes de que Google reembolse automáticamente.
    if (!reconocidaYa && ESTADOS_CON_ACCESO.has(estado)) {
      const urlAck =
        `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PAQUETE_ANDROID}` +
        `/purchases/subscriptions/${productId}/tokens/${purchaseToken}:acknowledge`;
      const respuestaAck = await fetch(urlAck, {
        method: "POST",
        headers: { Authorization: `Bearer ${tokenAcceso}`, "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      if (respuestaAck.ok) {
        await supabaseAdmin.rpc("marcar_compra_reconocida", { p_purchase_token: purchaseToken });
      }
      // Si falla aquí no es grave: ya se concedió el acceso arriba, y el
      // acknowledge del móvil (en Compras.kt) es la red de seguridad.
    }

    return new Response(
      JSON.stringify({
        ok: true,
        estado,
        premiumHasta: expiraEn,
        tieneAcceso: ESTADOS_CON_ACCESO.has(estado),
      }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (err) {
    console.error(err);
    return new Response(
      JSON.stringify({ error: "error_interno", detalle: String(err) }),
      { status: 500 },
    );
  }
});
