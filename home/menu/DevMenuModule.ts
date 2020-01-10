import { NativeModules, EventSubscription } from 'react-native';

import MockKernel from '../kernel/MockKernel';
import addListenerWithNativeCallback from '../utils/addListenerWithNativeCallback';

const NativeKernel = NativeModules.ExponentKernel || MockKernel;

export type KernelDevMenuItem = {
  label: string;
  isEnabled: boolean;
  detail?: string;
};

export async function closeAsync(): Promise<void> {
  return await NativeKernel.closeDevMenuAsync();
}

export async function getItemsToShowAsync(): Promise<{ [key: string]: KernelDevMenuItem }> {
  return await NativeKernel.getDevMenuItemsToShowAsync();
}

export async function isOnboardingFinishedAsync(): Promise<boolean> {
  return await NativeKernel.getIsNuxFinishedAsync();
}

export async function setOnboardingFinishedAsync(finished: boolean): Promise<void> {
  await NativeKernel.setIsNuxFinishedAsync(finished);
}

export async function selectItemWithKeyAsync(key: string): Promise<void> {
  await NativeKernel.selectDevMenuItemWithKeyAsync(key);
}

export async function reloadAppAsync(): Promise<void> {
  await NativeKernel.reloadAppAsync();
}

export async function goToHomeAsync(): Promise<void> {
  await NativeKernel.goToHomeAsync();
}

export function listenForCloseRequests(listener: () => void): EventSubscription {
  return addListenerWithNativeCallback('ExponentKernel.requestToCloseDevMenu', listener);
}
