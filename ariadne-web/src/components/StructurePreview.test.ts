import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { buildMaStructure } from '../lib/maStructure'

const { setOption } = vi.hoisted(() => ({ setOption: vi.fn() }))
vi.mock('echarts', () => ({ init: () => ({ setOption, dispose: vi.fn() }) }))

import StructurePreview from './StructurePreview.vue'

describe('StructurePreview', () => {
  it('renders five signed MA-to-NAV percentage bars without point labels', async () => {
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
    const option = setOption.mock.calls.at(-1)?.[0] as { xAxis: { data: string[]; axisLine: { onZero: boolean } }; yAxis: { min: number; max: number; axisLabel: { formatter: (value: number) => string } }; series: { type: string; label: { show: boolean }; data: unknown[]; itemStyle: { color: (params: { value: number }) => string } }[] }
    expect(option.xAxis.data).toEqual(['MA120', 'MA60', 'MA30', 'MA15', 'MA5'])
    expect(option.xAxis.axisLine.onZero).toBe(true)
    expect(option.yAxis.min).toBeCloseTo(-10)
    expect(option.yAxis.max).toBeCloseTo(10)
    expect(option.yAxis.axisLabel.formatter(1.5)).toBe('1.5%')
    expect(option.series[0].type).toBe('bar')
    expect(option.series[0].label.show).toBe(false)
    expect(option.series[0].data).toHaveLength(5)
    expect(option.series[0].data[0]).toBeCloseTo(10)
    expect(option.series[0].data[1]).toBeCloseTo(-10)
    expect(option.series[0].data.slice(2)).toEqual([null, null, 0])
    expect(option.series[0].itemStyle.color({ value: 10 })).not.toBe(option.series[0].itemStyle.color({ value: -10 }))
    expect(wrapper.find('.structure-preview').attributes('style')).toContain('width: 380px')
    expect(wrapper.find('.structure-preview').attributes('style')).not.toContain('max-height')
    expect(wrapper.find('.preview-comparison caption').text()).toContain('（MA − NAV）/ NAV × 100%')
    expect(wrapper.findAll('.preview-comparison thead th').map((th) => th.text()))
      .toEqual(['相对净值', 'MA120', 'MA60', 'MA30', 'MA15', 'MA5', '单位净值'])
    expect(wrapper.findAll('.preview-comparison tbody tr').map((tr) => tr.findAll('th').map((th) => th.text())))
      .toEqual([['绝对值'], ['百分比']])
    expect(wrapper.findAll('.preview-comparison tbody tr').at(0)?.findAll('td').map((td) => td.text()))
      .toEqual(['1.1', '0.9', '缺失', '缺失', '1', '1'])
    expect(wrapper.findAll('.preview-comparison tbody tr').at(1)?.findAll('td').map((td) => td.text()))
      .toEqual(['+10.00%', '-10.00%', '—', '—', '0.00%', '基准'])
    expect(wrapper.find('.visually-hidden').text()).toContain('单位净值1')
    expect(wrapper.find('.visually-hidden').text()).toContain('MA30缺失')
    expect(wrapper.find('.preview-chart').attributes('aria-hidden')).toBe('true')
    expect(wrapper.find('.preview-chart-note').text()).toContain('不是收益率')
    await wrapper.setProps({ structure: buildMaStructure({ date: '2026-09-23', unitNav: null, movingAverages: {
      MA120: { value: '1.10', deviationPercent: null },
    } }) })
    expect(setOption.mock.calls.at(-1)?.[0].series[0].data).toEqual([null, null, null, null, null])
    expect(wrapper.findAll('.preview-comparison tbody tr').at(0)?.findAll('td').map((td) => td.text()))
      .toEqual(['1.1', '缺失', '缺失', '缺失', '缺失', '缺失'])
    expect(wrapper.findAll('.preview-comparison tbody tr').at(1)?.findAll('td').map((td) => td.text()))
      .toEqual(['—', '—', '—', '—', '—', '—'])
    await wrapper.setProps({ structure: buildMaStructure({ date: '2026-09-24', unitNav: '0', movingAverages: {
      MA120: { value: '1.10', deviationPercent: null },
    } }) })
    expect(setOption.mock.calls.at(-1)?.[0].series[0].data).toEqual([null, null, null, null, null])
    expect(wrapper.findAll('.preview-comparison tbody tr').at(0)?.findAll('td').at(5)?.text()).toBe('0')
    expect(wrapper.findAll('.preview-comparison tbody tr').at(1)?.findAll('td').at(0)?.text()).toBe('—')
    expect(wrapper.findAll('.preview-comparison tbody tr').at(1)?.findAll('td').at(5)?.text()).toBe('基准')
    await wrapper.setProps({ narrow: true })
    expect(wrapper.find('.structure-preview').classes()).toContain('narrow')
    expect(wrapper.find('.preview-comparison').exists()).toBe(true)
    wrapper.unmount()
  })

  it('keeps the 0% baseline for positive-only, negative-only and all-zero bars', async () => {
    const structure = (ma: string) => buildMaStructure({ date: '2026-09-25', unitNav: '1', movingAverages: {
      MA120: { value: ma, deviationPercent: null },
    } })
    const wrapper = mount(StructurePreview, { props: { structure: structure('1.1'), pinned: false, narrow: false, reducedMotion: true } })
    await flushPromises()
    expect(setOption.mock.calls.at(-1)?.[0].yAxis.min).toBe(0)
    expect(setOption.mock.calls.at(-1)?.[0].yAxis.max).toBeCloseTo(10)
    await wrapper.setProps({ structure: structure('0.9') })
    expect(setOption.mock.calls.at(-1)?.[0].yAxis.min).toBeCloseTo(-10)
    expect(setOption.mock.calls.at(-1)?.[0].yAxis.max).toBe(0)
    await wrapper.setProps({ structure: structure('1') })
    expect(setOption.mock.calls.at(-1)?.[0].series[0].data).toEqual([0, null, null, null, null])
    expect(setOption.mock.calls.at(-1)?.[0].yAxis).toMatchObject({ min: -1, max: 1 })
    wrapper.unmount()
  })

  it('keeps full-precision decimal values in the wrapping absolute row', async () => {
    const structure = buildMaStructure({
      date: '2026-09-25', unitNav: '1.1234567891', movingAverages: {
        MA120: { value: '2.2345678912', deviationPercent: null },
        MA60: { value: '0.9876543219', deviationPercent: null },
        MA30: { value: '12.3456789012', deviationPercent: null },
        MA5: { value: '3.1415926535', deviationPercent: null },
      },
    })
    const wrapper = mount(StructurePreview, { props: { structure, pinned: false, narrow: false, reducedMotion: true } })
    await flushPromises()
    const rows = wrapper.findAll('.preview-comparison tbody tr')
    expect(rows.at(0)?.classes()).toContain('preview-absolute')
    expect(rows.at(1)?.classes()).toContain('preview-percent')
    expect(rows.at(0)?.findAll('td').map((td) => td.text()))
      .toEqual(['2.2345678912', '0.9876543219', '12.3456789012', '缺失', '3.1415926535', '1.1234567891'])
    expect(wrapper.find('.preview-absolute').text()).toContain('12.3456789012')
    wrapper.unmount()
  })
})
