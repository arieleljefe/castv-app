package com.castv.app

import androidx.core.content.FileProvider

/**
 * [AUTORESPALDO-1] Entrega a otras apps (WhatsApp, correo, Bluetooth…) el archivo `.json` que se arma
 * en la carpeta temporal `cache/share/` al compartir. Es independiente del proveedor de UPDATE-1
 * (otra autoridad: `${applicationId}.shareprovider`, otras rutas: `share_paths.xml`).
 */
class ShareProvider : FileProvider()
