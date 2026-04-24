import type {
  LoginRequest,
  RegisterRequest,
  TokenResponse,
  UpdateProfileRequest,
  User
} from '@/types'
import { get, post, put } from './axios'

export function login(payload: LoginRequest) {
  return post<TokenResponse>('/user/login', payload)
}

export function register(payload: RegisterRequest) {
  return post<User>('/user/register', payload)
}

export function fetchCurrentUser() {
  return get<User>('/user/info')
}

export function searchUsers(keyword: string) {
  return get<User[]>('/user/search', {
    params: {
      keyword
    }
  })
}

export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)

  return post<string>('/user/avatar/upload', formData)
}

export function updateProfile(payload: UpdateProfileRequest) {
  return put<User>('/user/profile', payload)
}
