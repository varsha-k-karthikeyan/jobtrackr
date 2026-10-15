import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BoardPage } from './board-page';

describe('BoardPage', () => {
  let component: BoardPage;
  let fixture: ComponentFixture<BoardPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BoardPage],
      // Test doubles: the page loads data on init, but no real request may leave the test
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(BoardPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
