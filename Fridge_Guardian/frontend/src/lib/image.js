export const MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

/** 서버와 같은 기준(5MB, JPG·PNG·WEBP)으로 먼저 걸러 냅니다. 서버도 내용을 다시 검사하므로 이 검사는 안내용입니다. */
export function validateImage(file) {
  if (!IMAGE_TYPES.includes(file.type)) return 'JPG, PNG, WEBP 이미지만 올릴 수 있어요.'
  if (file.size > MAX_IMAGE_BYTES) return '이미지는 5MB 이하만 올릴 수 있어요.'
  return ''
}
