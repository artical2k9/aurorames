import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { CharacteristicEditorComponent } from './characteristic-editor.component';
import { InspectionPlanApiService } from '../../services/inspection-plan-api.service';

describe('CharacteristicEditorComponent', () => {
  let fixture: ComponentFixture<CharacteristicEditorComponent>;
  let component: CharacteristicEditorComponent;

  const mockApi = {
    listCharacteristics: vi.fn().mockReturnValue(of([])),
    addCharacteristic: vi.fn().mockReturnValue(of({})),
    updateCharacteristic: vi.fn().mockReturnValue(of({})),
    deleteCharacteristic: vi.fn().mockReturnValue(of(undefined)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [CharacteristicEditorComponent],
      providers: [
        provideNoopAnimations(),
        { provide: InspectionPlanApiService, useValue: mockApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CharacteristicEditorComponent);
    component = fixture.componentInstance;
    component.planId = 'plan-1';
    component.editable = true;
  });

  it('SPECIFIC requires at least one limit to save', () => {
    component.openAdd();
    component.draft.characteristicType = 'SPECIFIC';
    component.draft.characteristicNumber = 10;
    component.draft.name = 'Bore';
    component.draft.sampleSizeRule = 'ALL';
    expect(component.canSave()).toBe(false);
    component.draft.nominalValue = 25.4;
    expect(component.canSave()).toBe(true);
  });

  it('COMMON requires expectedBoolean to save', () => {
    component.openAdd();
    component.draft.characteristicType = 'COMMON';
    component.draft.characteristicNumber = 20;
    component.draft.name = 'Cleanliness';
    component.draft.sampleSizeRule = 'ALL';
    expect(component.canSave()).toBe(false);
    component.draft.expectedBoolean = true;
    expect(component.canSave()).toBe(true);
  });

  it('CALCULATED requires expression to save', () => {
    component.openAdd();
    component.draft.characteristicType = 'CALCULATED';
    component.draft.characteristicNumber = 30;
    component.draft.name = 'Derived';
    component.draft.sampleSizeRule = 'ALL';
    expect(component.canSave()).toBe(false);
    component.draft.expression = 'C10 * 2';
    expect(component.canSave()).toBe(true);
  });

  it('FIXED_COUNT requires a sample count', () => {
    component.openAdd();
    component.draft.characteristicType = 'SPECIFIC';
    component.draft.characteristicNumber = 10;
    component.draft.name = 'Bore';
    component.draft.nominalValue = 1;
    component.draft.sampleSizeRule = 'FIXED_COUNT';
    expect(component.canSave()).toBe(false);
    component.draft.sampleSizeCount = 5;
    expect(component.canSave()).toBe(true);
  });

  it('onTypeChange clears fields from other types', () => {
    component.draft = {
      characteristicType: 'CALCULATED', expression: 'C10', nominalValue: 5, expectedBoolean: true,
    };
    component.onTypeChange();
    expect(component.draft.expression).toBeUndefined();
    expect(component.draft.nominalValue).toBeUndefined();
    expect(component.draft.expectedBoolean).toBeUndefined();
  });

  it('specSummary renders by type', () => {
    expect(component.specSummary({
      characteristicType: 'CALCULATED', expression: '(C10+C20)/2',
    } as never)).toBe('(C10+C20)/2');
    expect(component.specSummary({
      characteristicType: 'COMMON', expectedBoolean: true,
    } as never)).toContain('true');
  });
});
