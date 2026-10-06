
# 😈 DemoniTalk (v2.0.1 - DemonicAI Edition)   ![Android](https://img.shields.io/badge/Android-181717?style=flat&logo=android&logoColor=FF8B3D) ![Kotlin](https://img.shields.io/badge/kotlin-181717?style=flat&logo=kotlin&logoColor=FF8B3D) 

## 😈 DemoniTalk — AI, Root & Accessibility Voice Automation Engine
    
<a href="https://github.com/Jaypsmall/DemoniTalk/releases/download/demoni/DemoniTalk_v2.0.1_AI.apk">
  <img src="https://img.shields.io/badge/DOWNLOAD_DEMONITALK_v2.0.1_AI_APK-181717?style=flat&logo=android&logoColor=FF8B3D" alt="Download AI Release">
<a href="https://github.com/Jaypsmall/DemoniTalk/releases/download/root/DemoniTalk_v1.0.3.apk">
  <img src="https://img.shields.io/badge/DOWNLOAD_DEMONITALK_v1.0.3_APK-181717?style=flat&logo=android&logoColor=FF8B3D" alt="Download Root Release">
</a>

> [!NOTE]
> **DemoniTalk** es un asistente de voz de vanguardia y un motor de automatización multinivel para Android. Combina la potencia del acceso a superusuario (Root), el motor de **Servicios de Accesibilidad (AccessibilityService API)** e integración con **IA / LLM local** para ejecutar scripts de consola, macros táctiles complejas e interacciones del sistema mediante voz e interfaz flotante.

---

<p align="center">
  <img src="https://github.com/Jaypsmall/DemoniTalk/raw/master-v2/assets/1.png" width="49%" />
  <img src="https://github.com/Jaypsmall/DemoniTalk/raw/master-v2/assets/2.png" width="49%" />
</p>

---

## ✨ Key Features

* **🤖 AI-Powered Assistant & Voice NLP:** Integración de Inteligencia Artificial para el procesamiento contextual de comandos de voz, interpretación natural del lenguaje y ejecución adaptativa de acciones.

* **♿ Accessibility Service Automation:** Utiliza la API de `AccessibilityService` de Android para leer nodos de interfaz en pantalla, interactuar con elementos sin necesidad de coordenadas fijas y automatizar flujos dentro de cualquier aplicación.

* **🎙️ Interactive Floating Overlay:** Módulo de escucha interactivo mediante un botón flotante siempre visible sobre cualquier app o juego.

* **⚡ Root-Powered Execution (Custom Triggers):**
  * **Process Control:** Cierre forzado inmediato de aplicaciones (`am force-stop`) o finalización de procesos por PID.
  * **Touch & Gesture Simulation:** Creación de macros complejas encadenando coordenadas de toque (`input tap`), gestos (`input swipe`) y temporizadores (`sleep`).
  * **System Keyevent Injection:** Inyección de acciones nativas del sistema (Atrás, Inicio, Apps Recientes) mediante comandos de terminal de bajo nivel.

* **🔄 Dynamic Capture Modes:**
  * **Continuous Mode:** Escucha activa continua para el procesamiento encadenado de intenciones y comandos de voz.
  * **Push-to-Talk (Manual):** Activación por toque para optimizar consumo de batería y memoria.

* **🎨 "Demonic Edition" Interface:** Diseño oscuro agresivo y optimizado, con gestor de diccionarios (Importación/Exportación de perfiles) y soporte para temas.

---

<p align="center">
  <img src="https://github.com/Jaypsmall/DemoniTalk/raw/master-v2/assets/4.png" width="49%" />
  <img src="https://github.com/Jaypsmall/DemoniTalk/raw/master-v2/assets/5.png" width="49%" />  
</p>

---

## 🛠️ Tech Stack & Requirements

> [!NOTE]
> | Componente | Especificación Técnica |
> | :--- | :--- |
> | **Lenguaje & Core** | `Kotlin` · Android SDK · Asynchronous Coroutines |
> | **IA & NLP** | Integración con motor de procesamiento de lenguaje nativo y modelos locales / API LLM |
> | **Accesibilidad** | `AccessibilityService API` para inspección de UI y automatización sin root |
> | **Superusuario (Root)** | Integración con `Magisk` / `KernelSU` para inyección de comandos en shell `su` |
> | **Overlay & Windowing** | Permiso `SYSTEM_ALERT_WINDOW` para la gestión de la interfaz flotante interactiva |

---

## 📄 Licencia y Copyright

Todos los derechos reservados © 2026.

El código fuente de esta aplicación es propiedad privada del desarrollador (**Jaypsmall**). Se prohíbe la reproducción, redistribución, ingeniería inversa o modificación no autorizada de este software.

*Desarrollado con 💙 por un desarrollador independiente.*
