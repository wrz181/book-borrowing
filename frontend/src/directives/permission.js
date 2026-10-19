import { useUserStore } from '../stores/user'

/**
 * v-permission 指令：无权限时移除元素。
 * 用法：v-permission="'book:add'" 或 v-permission="['book:add','book:edit']"（满足任一即可）。
 */
export const permission = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const value = binding.value
    if (!value) return
    const codes = Array.isArray(value) ? value : [value]
    const ok = codes.some((c) => userStore.permissions.includes(c))
    if (!ok && el.parentNode) {
      el.parentNode.removeChild(el)
    }
  }
}
