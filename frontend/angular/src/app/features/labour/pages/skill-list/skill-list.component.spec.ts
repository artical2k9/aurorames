import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { SkillListComponent } from './skill-list.component';
import { LabourApiService } from '../../services/labour-api.service';
import { UdfApiService } from '../../../../shared/udf/udf-api.service';
import { SkillDto } from '../../models/labour.model';

const MOCK_SKILL: SkillDto = {
  id: 'skill-1',
  skillCode: 'WELD-A',
  name: 'TIG Welding A',
  category: 'Welding',
  certificationRequired: true,
  validityMonths: 24,
  active: true,
  customFields: { rig: 'Bay-3' },
};

describe('SkillListComponent', () => {
  let fixture: ComponentFixture<SkillListComponent>;
  let component: SkillListComponent;

  const mockApi = {
    listSkills: vi.fn().mockReturnValue(of({ content: [MOCK_SKILL], totalElements: 1 })),
    createSkill: vi.fn().mockReturnValue(of({ ...MOCK_SKILL })),
    patchSkill: vi.fn().mockReturnValue(of({ ...MOCK_SKILL, active: false })),
  };

  const mockUdfApi = {
    listFields: vi.fn().mockReturnValue(of([
      { fieldKey: 'rig', label: 'Rig', fieldType: 'TEXT' },
    ])),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [SkillListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: LabourApiService, useValue: mockApi },
        { provide: UdfApiService, useValue: mockUdfApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SkillListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads UDF columns for the SKILL module on init', () => {
    expect(mockUdfApi.listFields).toHaveBeenCalledWith('SKILL');
  });

  it('fetches skills and exposes rows', async () => {
    await new Promise(resolve => setTimeout(resolve));
    expect(mockApi.listSkills).toHaveBeenCalled();
    expect(component.rows).toHaveLength(1);
    expect(component.totalRecords).toBe(1);
    expect(component.loading).toBe(false);
  });

  it('getCellValue reads direct properties for standard columns', () => {
    const value = component.getCellValue(MOCK_SKILL, {
      key: 'skillCode', label: 'Code', visible: true, order: 0,
    });
    expect(value).toBe('WELD-A');
  });

  it('getCellValue falls back to customFields for UDF columns', () => {
    const value = component.getCellValue(MOCK_SKILL, {
      key: 'rig', label: 'Rig', visible: true, order: 6, udf: true,
    });
    expect(value).toBe('Bay-3');
  });

  it('clears rows and stops loading on fetch error', () => {
    mockApi.listSkills.mockReturnValueOnce(throwError(() => new Error('boom')));
    component['fetchRows']();
    expect(component.rows).toHaveLength(0);
    expect(component.loading).toBe(false);
  });
});
