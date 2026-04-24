import type { InspirationResponse } from '@/types'
import { get } from './axios'

export function getDailyInspiration() {
  return get<InspirationResponse>('/daily-inspiration')
}
