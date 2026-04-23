<template>
  <div ref="chartEl" :style="{ width: '100%', height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '380px' },
})

const chartEl = ref(null)
let chart = null
let resizeObs = null

onMounted(() => {
  chart = echarts.init(chartEl.value)
  chart.setOption(props.option)
  resizeObs = new ResizeObserver(() => chart && chart.resize())
  resizeObs.observe(chartEl.value)
})

watch(() => props.option, (val) => {
  if (chart) chart.setOption(val, true)
}, { deep: true })

onBeforeUnmount(() => {
  if (resizeObs) resizeObs.disconnect()
  if (chart) { chart.dispose(); chart = null }
})
</script>
