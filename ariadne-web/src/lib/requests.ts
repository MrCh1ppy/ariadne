export class RequestGate {
  private sequence = 0
  private controller: AbortController | null = null

  next(): { id: number; signal: AbortSignal } {
    this.controller?.abort()
    this.controller = new AbortController()
    return { id: ++this.sequence, signal: this.controller.signal }
  }

  isCurrent(id: number): boolean {
    return id === this.sequence
  }

  cancel(): void {
    this.controller?.abort()
    this.sequence++
  }
}
