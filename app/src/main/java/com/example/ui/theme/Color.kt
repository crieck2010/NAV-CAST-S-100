package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Marine ECDIS & NOAA Rugged Nautical Palette
val NavyBackground = Color(0xFF0B132B)       // Dark Slate / Deep Marine Navy (as specified: #0B132B)
val NavySurface = Color(0xFF141E38)          // Instrument console surface
val NavySurfaceVariant = Color(0xFF1E2D52)   // Card elevated surface
val NavySurfaceHighlight = Color(0xFF283B68) // Border & divider highlight

val NeonCyan = Color(0xFF48CAE4)             // Neon Cyan (as specified: #48CAE4)
val OceanicBlue = Color(0xFF00B4D8)          // Vivid oceanic blue
val DeepOcean = Color(0xFF0077B6)            // Medium nautical blue

// NOAA Official Institutional Accents
val NoaaCobalt = Color(0xFF003366)           // NOAA Official Deep Navy
val NoaaBlue = Color(0xFF004B87)             // NOAA Seal Blue
val NoaaGold = Color(0xFFEAA812)             // NOAA Compass Gold
val ChartMagenta = Color(0xFFD90429)         // Nautical chart magenta for dredged limits & aids

// Tactical Alerts & IHO S-52 Symbology
val AmberAlert = Color(0xFFFFB703)           // Caution / Warning threshold
val CoralDanger = Color(0xFFE63946)          // Shoal danger / Under-keel breach
val EmeraldSafe = Color(0xFF06D6A0)          // Safe passage / Port lateral green in IALA-B
val LateralRed = Color(0xFFEF233C)           // Starboard lateral red in IALA-B

// Text & Monospace Telemetry
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextTertiary = Color(0xFF64748B)

// S-102 Bathymetry Depth Color Ramp
val BathymetryDeep = Color(0xFF03045E)       // Deep fairway (> 14m)
val BathymetryMedium = Color(0xFF0077B6)     // Medium navigable water (9m - 14m)
val BathymetryShallow = Color(0xFF0096C7)    // Shallow water (5m - 9m)
val BathymetryShoal = Color(0xFFE63946)      // Critical shoal / drying (< 5m)

// Grid & Chart Lines
val GridLineColor = Color(0x3348CAE4)
val ContourLineColor = Color(0x6648CAE4)
val ChannelBoundaryColor = Color(0x8806D6A0)
