import { api, errorText } from '../api'
import { useShell } from '../shell'

/** Bloklash / qayta faollashtirish — sabab majburiy, auditga yoziladi. `true` — o'zgardi. */
export function useSellerStatusAction() {
  const { confirm, toast } = useShell()
  return async (tg: number, status: string): Promise<boolean> => {
    const suspend = status === 'ACTIVE'
    const reason = await confirm(suspend
      ? { title: 'Sotuvchini bloklash', description: `Sotuvchi ${tg} botdan foydalana olmaydi. Ma’lumotlari saqlanadi, faol kirish ruxsatlari bekor qilinadi.`, confirmLabel: 'Bloklash', tone: 'danger', requireReason: true }
      : { title: 'Qayta faollashtirish', description: `Sotuvchi ${tg} botdan yana foydalana oladi.`, confirmLabel: 'Faollashtirish', tone: 'warning', requireReason: true })
    if (reason === null) return false
    try {
      await api(`/sellers/${tg}/${suspend ? 'suspend' : 'reactivate'}`, { method: 'POST', body: { reason } })
      toast({ tone: 'success', title: suspend ? 'Sotuvchi bloklandi' : 'Sotuvchi faollashtirildi', audit: true })
      return true
    } catch (e) {
      toast({ tone: 'danger', title: errorText(e) })
      return false
    }
  }
}
