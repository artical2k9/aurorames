import { Pipe, PipeTransform } from '@angular/core';
import { Classification } from '../models/item-master.model';

@Pipe({ name: 'classificationLabel', standalone: true })
export class ClassificationLabelPipe implements PipeTransform {
  transform(value: Classification): string {
    switch (value) {
      case 'ASSEMBLY':       return 'ASSEMBLY';
      case 'COTS':           return 'COTS';
      case 'FABRICATED':     return 'FABRICATED';
      case 'PURCHASED_PART': return 'PURCHASED';
      case 'RAW_MATERIAL':   return 'RAW MATERIAL';
      case 'SERVICE':        return 'SERVICE';
      default:               return value;
    }
  }
}
