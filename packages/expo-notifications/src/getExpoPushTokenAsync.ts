import { Platform } from '@unimodules/core';
import * as Application from 'expo-application';
import Constants from 'expo-constants';

import getDevicePushTokenAsync, { DevicePushToken } from './getDevicePushTokenAsync';

const productionBaseUrl = 'https://exp.host/--/api/v2/';

export interface ExpoPushToken {
  type: 'expo';
  data: string;
}

interface Options {
  // Endpoint URL override
  baseUrl?: string;

  // Request URL override
  url?: string;

  // Request body overrides
  type?: string;
  appId?: string;
  deviceId?: string;
  development?: boolean;
  experienceId?: string;
  devicePushToken?: DevicePushToken;
}

export default async function getExpoPushTokenAsync(options: Options = {}): Promise<ExpoPushToken> {
  const devicePushToken = options.devicePushToken || (await getDevicePushTokenAsync());

  const deviceId = options.deviceId || (await getDeviceIdAsync());

  const experienceId =
    options.experienceId ||
    (Constants.manifest && `@${Constants.manifest.owner}/${Constants.manifest.slug}`);

  if (!experienceId) {
    throw new Error(
      "No experienceId found. If it can't be inferred from the manifest (eg. in bare workflow), you have to pass it in yourself."
    );
  }

  const appId = options.appId || Application.applicationId || experienceId;
  const type = options.type || getTypeOfToken(devicePushToken);
  const development = options.development || (await shouldUseDevelopmentNotificationService());

  const url = options.url || `${options.baseUrl || productionBaseUrl}push/getExpoPushToken`;

  const body = {
    type,
    appId,
    deviceId,
    development,
    experienceId,
    deviceToken: getDeviceToken(devicePushToken),
  };

  const response = await fetch(url, {
    method: 'POST',
    body: JSON.stringify(body),
  }).catch(error => {
    throw new Error(`Error encountered while fetching Expo token: ${error}.`);
  });

  if (!response.ok) {
    const statusInfo = response.statusText || response.status;
    throw new Error(
      `Error encountered while fetching Expo token, expected an OK response, received: ${statusInfo}`
    );
  }

  const expoPushToken = getExpoPushToken(await parseResponse(response));

  return {
    type: 'expo',
    data: expoPushToken,
  };
}

async function parseResponse(response: Response) {
  try {
    return await response.json();
  } catch (error) {
    try {
      throw new Error(
        `Expected a JSON response from server when fetching Expo token, received body: ${JSON.stringify(
          await response.text()
        )}.`
      );
    } catch (innerError) {
      throw new Error(
        `Expected a JSON response from server when fetching Expo token, received response: ${JSON.stringify(
          response
        )}.`
      );
    }
  }
}

function getExpoPushToken(data: any) {
  if (
    !data ||
    !(typeof data === 'object') ||
    !data.data ||
    !(typeof data.data === 'object') ||
    !data.data.expoPushToken ||
    !(typeof data.data.expoPushToken === 'string')
  ) {
    throw new Error(
      `Malformed response from server, expected "{ data: { expoPushToken: string } }", received: ${JSON.stringify(
        data,
        null,
        2
      )}.`
    );
  }

  return data.data.expoPushToken as string;
}

async function getDeviceIdAsync() {
  let platformSpecificDeviceId: string | null = null;
  try {
    switch (Platform.OS) {
      case 'ios':
        platformSpecificDeviceId = await Application.getIosIdForVendorAsync();
        break;
      case 'android':
        platformSpecificDeviceId = Application.androidId;
    }
  } catch (e) {
    // there's nothing we can do, let's fallback to using Constants
  }

  return platformSpecificDeviceId || Constants.installationId;
}

function getDeviceToken(devicePushToken: DevicePushToken) {
  if (typeof devicePushToken.data === 'string') {
    return devicePushToken.data;
  }

  return JSON.stringify(devicePushToken.data);
}

async function shouldUseDevelopmentNotificationService() {
  if (Platform.OS === 'ios') {
    try {
      const notificationServiceEnvironment = await Application.getIosPushNotificationServiceEnvironmentAsync();
      if (notificationServiceEnvironment === 'development') {
        return true;
      }
    } catch (e) {
      // We can't do anything here, we'll fallback to false then.
    }
  }

  return false;
}

function getTypeOfToken(devicePushToken: DevicePushToken) {
  switch (devicePushToken.type) {
    case 'ios':
      return 'apns';
    case 'android':
      return 'fcm';
    // This probably will error on server, but let's make this function future-safe.
    default:
      return devicePushToken.type;
  }
}
