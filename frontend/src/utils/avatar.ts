export interface AvatarPreset {
  id: string
  label: string
  value: string
  preview: string
}

type AvatarPresetDefinition = {
  id: string
  label: string
  backgroundStart: string
  backgroundEnd: string
  accentPrimary: string
  accentSecondary: string
}

const AVATAR_PRESET_DEFINITIONS: AvatarPresetDefinition[] = [
  {
    id: 'terracotta',
    label: '暖阳',
    backgroundStart: '#b85c38',
    backgroundEnd: '#f2b880',
    accentPrimary: '#fff4d8',
    accentSecondary: '#ffe2b1'
  },
  {
    id: 'moss',
    label: '青苔',
    backgroundStart: '#4f6f52',
    backgroundEnd: '#9ec8b9',
    accentPrimary: '#eef8f1',
    accentSecondary: '#d1edd8'
  },
  {
    id: 'ocean',
    label: '海盐',
    backgroundStart: '#2b4c7e',
    backgroundEnd: '#70a1d7',
    accentPrimary: '#eef6ff',
    accentSecondary: '#d9ebff'
  },
  {
    id: 'plum',
    label: '暮紫',
    backgroundStart: '#5c3d6d',
    backgroundEnd: '#c38eb4',
    accentPrimary: '#fff1fb',
    accentSecondary: '#f3d5ea'
  },
  {
    id: 'citrus',
    label: '柑橘',
    backgroundStart: '#c86b1f',
    backgroundEnd: '#ffd166',
    accentPrimary: '#fff7d6',
    accentSecondary: '#ffe8a3'
  },
  {
    id: 'midnight',
    label: '夜蓝',
    backgroundStart: '#23395d',
    backgroundEnd: '#406e8e',
    accentPrimary: '#edf4ff',
    accentSecondary: '#d7e6fb'
  }
]

function svgToDataUri(svg: string): string {
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

function buildAvatarSvg(definition: AvatarPresetDefinition): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" fill="none">
    <defs>
      <linearGradient id="bg-${definition.id}" x1="18" y1="18" x2="102" y2="102" gradientUnits="userSpaceOnUse">
        <stop stop-color="${definition.backgroundStart}" />
        <stop offset="1" stop-color="${definition.backgroundEnd}" />
      </linearGradient>
    </defs>
    <rect width="120" height="120" rx="60" fill="url(#bg-${definition.id})" />
    <circle cx="42" cy="40" r="22" fill="${definition.accentSecondary}" opacity="0.94" />
    <circle cx="78" cy="78" r="28" fill="${definition.accentPrimary}" opacity="0.9" />
    <path d="M31 83c7-13 20-21 34-21 10 0 18 3 25 8 4 3 4 9 0 12-8 7-19 11-31 11-11 0-21-3-29-9-4-3-4-8 1-11Z" fill="${definition.accentPrimary}" />
    <path d="M53 33c0-5 4-9 9-9s9 4 9 9c0 5-4 9-9 9s-9-4-9-9Z" fill="${definition.backgroundStart}" opacity="0.85" />
    <path d="M48 61c3-6 8-9 14-9 6 0 11 3 14 9" stroke="${definition.backgroundStart}" stroke-width="7" stroke-linecap="round" opacity="0.88" />
  </svg>`
}

export const DEFAULT_AVATAR_PRESETS: AvatarPreset[] = AVATAR_PRESET_DEFINITIONS.map((definition) => ({
  id: definition.id,
  label: definition.label,
  value: `preset:${definition.id}`,
  preview: svgToDataUri(buildAvatarSvg(definition))
}))

const avatarPresetMap = new Map(DEFAULT_AVATAR_PRESETS.map((preset) => [preset.value, preset.preview]))

export function resolveAvatarSrc(avatar?: string | null): string | undefined {
  if (!avatar) {
    return undefined
  }

  if (avatarPresetMap.has(avatar)) {
    return avatarPresetMap.get(avatar)
  }

  return avatar
}

export function isPresetAvatar(avatar?: string | null): boolean {
  return Boolean(avatar && avatarPresetMap.has(avatar))
}
