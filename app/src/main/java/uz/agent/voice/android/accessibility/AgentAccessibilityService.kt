package uz.agent.voice.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Android Accessibility Service. Ekrandagi elementlarni o'qish, bosish, matn kiritish,
 * scroll qilish va oldingi/keyingi ilova (foreground package) ni aniqlash uchun.
 * Faqat foydalanuvchi ilova sozlamalarida qo'lda yoqishi bilan ishga tushadi.
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { lastForegroundPackage = it }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    companion object {
        @Volatile var instance: AgentAccessibilityService? = null
            private set

        @Volatile var lastForegroundPackage: String? = null
            private set

        fun isEnabled(): Boolean = instance != null

        fun back(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false

        fun home(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false

        fun recents(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false

        /** Bir necha marta ozgina kutib, oldingi ilova kutilganiga mos kelishini tekshiradi. */
        fun waitForForeground(expectedPackages: List<String>, timeoutMs: Long = 1500): Boolean {
            val step = 150L
            var waited = 0L
            while (waited < timeoutMs) {
                val cur = lastForegroundPackage
                if (cur != null && expectedPackages.any { cur == it }) return true
                Thread.sleep(step)
                waited += step
            }
            val cur = lastForegroundPackage
            return cur != null && expectedPackages.any { cur == it }
        }

        fun findNodeByText(text: String): AccessibilityNodeInfo? {
            val svc = instance ?: return null
            val root = svc.rootInActiveWindow ?: return null
            return search(root, text.lowercase())
        }

        private fun search(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
            val nodeText = node.text?.toString()?.lowercase()
            val desc = node.contentDescription?.toString()?.lowercase()
            if ((nodeText != null && nodeText.contains(text)) ||
                (desc != null && desc.contains(text))
            ) {
                return node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = search(child, text)
                if (found != null) return found
            }
            return null
        }

        fun clickText(text: String): Boolean {
            val node = findNodeByText(text) ?: return false
            var target: AccessibilityNodeInfo? = node
            var hops = 0
            while (target != null && !target.isClickable && hops < 8) {
                target = target.parent
                hops++
            }
            val finalTarget = target ?: node
            return finalTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        fun typeText(text: String): Boolean {
            val svc = instance ?: return false
            val root = svc.rootInActiveWindow ?: return false
            val field = findEditable(root) ?: return false
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
            )
            return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isEditable) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findEditable(child)
                if (found != null) return found
            }
            return null
        }

        fun scroll(forward: Boolean): Boolean {
            val svc = instance ?: return false
            val root = svc.rootInActiveWindow ?: return false
            val scrollable = findScrollable(root) ?: return false
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            return scrollable.performAction(action)
        }

        private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findScrollable(child)
                if (found != null) return found
            }
            return null
        }
    }
}
