import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getBulkDict } from '@/api/dict'

export const useDictStore = defineStore('dict', () => {
  const cache = ref({})  // { TYPE: [{itemCode, itemName, ...}] }

  async function load(types) {
    const missing = types.filter(t => !cache.value[t])
    if (missing.length === 0) return
    const r = await getBulkDict(missing)
    Object.assign(cache.value, r.data || {})
  }

  function get(type) { return cache.value[type] || [] }

  function label(type, code) {
    const item = (cache.value[type] || []).find(i => i.itemCode === code)
    return item ? item.itemName : code
  }

  return { cache, load, get, label }
})
