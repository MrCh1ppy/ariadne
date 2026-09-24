import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { buildMaStructure } from '../lib/maStructure'

vi.mock('echarts', () => ({ init: () => ({ setOption: vi.fn(), dispose: vi.fn() }) }))

import StructurePreview from './StructurePreview.vue'

describe('StructurePreview', () => {
  it('shows ordered MA-to-NAV percentages, missing markers and accessible raw chart values', async () => {
    const structure = buildMaStructure({
      date: '2026-09-22', unitNav: '1.00', movingAverages: {
        MA120: { value: '1.10', deviationPercent: '-9.09' },
        MA60: { value: '0.90', deviationPercent: '11.11' },
        MA30: { value: null, deviationPercent: null },
        MA5: { value: '1.00', deviationPercent: '0.00' },
      },
    })
    const wrapper = mount(StructurePreview, { props: { structure, pinned: false, narrow: false, reducedMotion: true, targetStyle: { width: '380px' } } })
    await flushPromises()
    expect(wrapper.find('.structure-preview').attributes('style')).toContain('width: 380px')
    expect(wrapper.find('.structure-preview').attributes('style')).not.toContain('max-height')
    expect(wrapper.find('.preview-comparison caption').text()).toContain('（MA − NAV）/ NAV × 100%')
    expect(wrapper.findAll('.preview-comparison thead th').map((th) => th.text()))
      .toEqual(['相对净值', 'MA120', 'MA60', 'MA30', 'MA15', 'MA5'])
    expect(wrapper.findAll('.preview-comparison tbody td').map((td) => td.text()))
      .toEqual(['+10.00%', '-10.00%', '—', '—', '0.00%'])
    expect(wrapper.find('.visually-hidden').text()).toContain('单位净值1')
    expect(wrapper.find('.visually-hidden').text()).toContain('MA30缺失')
    expect(wrapper.find('.preview-chart').attributes('aria-hidden')).toBe('true')
    await wrapper.setProps({ structure: buildMaStructure({ date: '2026-09-23', unitNav: null, movingAverages: {
      MA120: { value: '1.10', deviationPercent: null },
    } }) })
    expect(wrapper.findAll('.preview-comparison tbody td').map((td) => td.text())).toEqual(['—', '—', '—', '—', '—'])
    await wrapper.setProps({ structure: buildMaStructure({ date: '2026-09-24', unitNav: '0', movingAverages: {
      MA120: { value: '1.10', deviationPercent: null },
    } }) })
    expect(wrapper.find('.preview-comparison tbody td').text()).toBe('—')
    await wrapper.setProps({ narrow: true })
    expect(wrapper.find('.structure-preview').classes()).toContain('narrow')
    expect(wrapper.find('.preview-comparison').exists()).toBe(true)
    wrapper.unmount()
  })
})
