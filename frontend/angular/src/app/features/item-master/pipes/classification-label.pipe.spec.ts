import { ClassificationLabelPipe } from './classification-label.pipe';

describe('ClassificationLabelPipe', () => {
  const pipe = new ClassificationLabelPipe();

  it('maps ASSEMBLY', () => expect(pipe.transform('ASSEMBLY')).toBe('ASSEMBLY'));
  it('maps COTS', () => expect(pipe.transform('COTS')).toBe('COTS'));
  it('maps FABRICATED', () => expect(pipe.transform('FABRICATED')).toBe('FABRICATED'));
  it('maps PURCHASED_PART to PURCHASED', () => expect(pipe.transform('PURCHASED_PART')).toBe('PURCHASED'));
  it('maps RAW_MATERIAL to RAW MATERIAL', () => expect(pipe.transform('RAW_MATERIAL')).toBe('RAW MATERIAL'));
  it('maps SERVICE', () => expect(pipe.transform('SERVICE')).toBe('SERVICE'));
});
