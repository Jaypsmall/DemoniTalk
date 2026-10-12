# Accessibility Statement for Demonitalk

## Priorities & Commitment

Demonitor is an accessibility-focused Android application designed to assist users with motor, visual, or situational limitations through voice commands and automated UI interaction.

Our priority is to make Demonitor fully accessible, reliable, and seamless to use with assistive technologies. The core architecture relies on `DemoniAccessibilityService` and Android's Accessibility APIs to enable hands-free navigation and system task automation.

We prioritize accessibility in the following key areas:
- **Voice Control & Navigation:** Intuitive speech processing to execute UI actions and app features.
- **Screen Reader Compatibility:** Full compatibility with Android TalkBack and screen readers for all main UI components.
- **Visual Design:** Material 3 design system with dynamic high-contrast themes, scalable typography, and clear visual feedback for voice recognition states.
- **Keyboard & Switch Access:** Logical focus order and navigation support for external input devices.

---

## Contributor Expectations

We welcome contributions that improve the accessibility and usability of Demonitalk. 

Contributors should adhere to the following guardrails when submitting changes:
- Ensure all new Jetpack Compose UI elements include appropriate `contentDescription` or semantics properties.
- Test interactive components with TalkBack enabled to verify that content descriptions are accurate and concise.
- Verify that touch targets meet or exceed Android accessibility guidelines (minimum 48x48 dp).
- Do not hardcode text inside graphics or rely solely on color to convey essential state changes.

---

## Supported Environments

Demonitor has been evaluated and tested in the following Android environments:
- **Operating System:** Android 8.0 (API level 26) and higher.
- **Target Framework:** Native Android (Kotlin / Jetpack Compose / Material 3).
- **Assistive Technologies:** Android TalkBack, Voice Access, and native Accessibility Services (`DemoniAccessibilityService`).
- **Input Methods:** Built-in microphone voice input, touch screen, and external Bluetooth controllers/keyboards.

---

## Known Limitations & Active Work

Demonitor is continuously evolving to enhance its voice processing accuracy and accessibility features.

- **Complex Dynamic UI Overlay:** Certain third-party app interfaces may require custom voice command mappings due to non-standard view hierarchies. Workarounds are actively implemented in `DemoniAccessibilityService`.
- **Real-time Voice Status Indicators:** Audio visualizers are optimized for high contrast, but speech-to-text feedback is also mirrored via haptic and textual cues.

---

## Reporting Accessibility Issues

If you encounter an accessibility barrier or have feedback on how to improve Demonitor, please let us know!

1. Open a new issue in this repository under the **Issues** tab.
2. Select or tag the issue with `accessibility`.
3. Describe the issue, including:
   - Your Android version and device model.
   - The assistive technology in use (e.g., TalkBack, Voice Access).
   - The expected behavior vs. observed behavior.

*Note: Do not include sensitive personal information or disability disclosures in public issue reports.*

---

## Ownership & Maintenance

DemoniTalk is actively developed and maintained with a focus on core Android accessibility APIs. Accessibility reviews and updates to this statement are performed alongside major feature releases.
