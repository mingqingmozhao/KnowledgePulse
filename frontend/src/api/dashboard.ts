import type { DashboardResponse } from '@/types'
import { get } from './axios'

export function getDashboard() {
  return get<DashboardResponse>('/dashboard')
}
