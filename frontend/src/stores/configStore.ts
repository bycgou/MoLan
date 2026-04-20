import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LlmConfigVO, CreateLlmConfigRequest, UpdateLlmConfigRequest } from '@/types'
import { llmConfigApi } from '@/api'

export const useConfigStore = defineStore('config', () => {
  const configs = ref<LlmConfigVO[]>([])
  const loading = ref(false)

  const activeConfigs = computed(() => configs.value.filter((c) => c.isActive === 1))

  async function fetchConfigs() {
    loading.value = true
    try {
      const res = await llmConfigApi.getConfigList()
      if (res.code === 200) {
        configs.value = res.data
      }
    } finally {
      loading.value = false
    }
  }

  async function createConfig(data: CreateLlmConfigRequest) {
    const res = await llmConfigApi.createConfig(data)
    if (res.code === 200) {
      await fetchConfigs()
      return true
    }
    return false
  }

  async function updateConfig(configId: string, data: UpdateLlmConfigRequest) {
    const res = await llmConfigApi.updateConfig(configId, data)
    if (res.code === 200) {
      await fetchConfigs()
      return true
    }
    return false
  }

  async function deleteConfig(configId: string) {
    const res = await llmConfigApi.deleteConfig(configId)
    if (res.code === 200) {
      configs.value = configs.value.filter((c) => c.configId !== configId)
      return true
    }
    return false
  }

  async function testConnection(data: import('@/types').TestConnectionRequest) {
    const res = await llmConfigApi.testConnection(data)
    return res
  }

  function $reset() {
    configs.value = []
  }

  return {
    configs,
    loading,
    activeConfigs,
    fetchConfigs,
    createConfig,
    updateConfig,
    deleteConfig,
    testConnection,
    $reset,
  }
})
