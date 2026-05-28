<template>
  <div ref="graphContainer" style="width: 100%; height: 400px; border: 1px solid #e4e7ed; border-radius: 4px;"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted } from 'vue'
import { Network } from 'vis-network'
import { DataSet } from 'vis-data'
import type { CharacterGraph, CharacterGraphNode, CharacterGraphEdge } from '@/types'

const props = defineProps<{
  graph: CharacterGraph | null
}>()

const graphContainer = ref<HTMLElement | null>(null)
let network: Network | null = null

const roleColors: Record<string, string> = {
  '主角': '#409EFF',
  '配角': '#67C23A',
  '反派': '#F56C6C',
  '龙套': '#909399',
}

const renderGraph = () => {
  if (!graphContainer.value || !props.graph) return

  const nodes = new DataSet(
    props.graph.nodes.map((n: CharacterGraphNode) => ({
      id: n.charId,
      label: n.charName,
      color: roleColors[n.role] || '#E6A23C',
      font: { color: '#333' },
    }))
  )

  const edges = new DataSet(
    props.graph.edges.map((e: CharacterGraphEdge, i: number) => ({
      id: i,
      from: e.charId1,
      to: e.charId2,
      label: e.type,
      font: { size: 10, color: '#999' },
      title: e.description || e.type,
    }))
  )

  const options = {
    physics: { enabled: true, stabilization: { iterations: 100 } },
    interaction: { hover: true },
    edges: { smooth: { type: 'continuous' } },
  }

  if (network) {
    network.destroy()
  }
  network = new Network(graphContainer.value, { nodes, edges }, options)
}

watch(() => props.graph, renderGraph, { deep: true })
onMounted(renderGraph)
onUnmounted(() => { network?.destroy() })
</script>
