import React from 'react';
import { Clipboard, PixelRatio, StyleSheet, View } from 'react-native';

import * as DevMenu from './DevMenuModule';
import * as Kernel from '../kernel/Kernel';
import DevMenuButton from './DevMenuButton';
import { StyledView } from '../components/Views';
import requestCameraPermissionsAsync from '../utils/requestCameraPermissionsAsync';
import DevMenuTaskInfo from './DevMenuTaskInfo';
import DevMenuOnboarding from './DevMenuOnboarding';
import DevMenuCloseButton from './DevMenuCloseButton';
import DevMenuBottomSheetContext from './DevMenuBottomSheetContext';

type Props = {
  task: { [key: string]: any };
  uuid: string;
};

type State = {
  enableDevMenuTools: boolean;
  devMenuItems: { [key: string]: any };
  isOnboardingFinished: boolean;
  isLoaded: boolean;
};

// These are defined in EXVersionManager.m in a dictionary, ordering needs to be
// done here.
const DEV_MENU_ORDER = [
  'dev-live-reload',
  'dev-hmr',
  'dev-remote-debug',
  'dev-reload',
  'dev-perf-monitor',
  'dev-inspector',
];

const MENU_ITEMS_ICON_MAPPINGS = {
  'dev-hmr': 'run-fast',
  'dev-remote-debug': 'remote-desktop',
  'dev-perf-monitor': 'speedometer',
  'dev-inspector': 'border-style',
};

class DevMenuView extends React.PureComponent<Props, State> {
  static contextType = DevMenuBottomSheetContext;

  constructor(props, context) {
    super(props, context);

    this.state = {
      enableDevMenuTools: false,
      devMenuItems: {},
      isOnboardingFinished: false,
      isLoaded: false,
    };
  }

  componentDidMount() {
    this.loadStateAsync();
  }

  componentDidUpdate(prevProps: Props) {
    if (this.props.uuid !== prevProps.uuid) {
      this.loadStateAsync();
    }
  }

  collapseAndCloseDevMenuAsync = async () => {
    if (this.context) {
      await this.context.collapse();
    }
    await DevMenu.closeAsync();
  };

  loadStateAsync = async () => {
    this.setState({ isLoaded: false }, async () => {
      const enableDevMenuTools = await Kernel.doesCurrentTaskEnableDevtoolsAsync();
      const devMenuItems = await DevMenu.getItemsToShowAsync();
      const isOnboardingFinished = await DevMenu.isOnboardingFinishedAsync();

      this.setState({
        enableDevMenuTools,
        devMenuItems,
        isOnboardingFinished,
        isLoaded: true,
      });
    });
  };

  onAppReload = () => {
    DevMenu.reloadAppAsync();
  };

  onCopyTaskUrl = () => {
    const { manifestUrl } = this.props.task;

    this.collapseAndCloseDevMenuAsync();
    Clipboard.setString(manifestUrl);
    alert(`Copied "${manifestUrl}" to the clipboard!`);
  };

  onGoToHome = () => {
    DevMenu.goToHomeAsync();
  };

  onPressDevMenuButton = key => {
    DevMenu.selectItemWithKeyAsync(key);
  };

  onOnboardingFinished = () => {
    DevMenu.setOnboardingFinishedAsync(true);
    this.setState({ isOnboardingFinished: true });
  };

  render() {
    if (!this.state.isLoaded) {
      return <StyledView style={styles.container} darkBackgroundColor="#000" />;
    }

    const { task } = this.props;
    const { isOnboardingFinished } = this.state;

    return (
      <StyledView style={styles.container} darkBackgroundColor="#000">
        {isOnboardingFinished ? (
          <DevMenuTaskInfo task={task} />
        ) : (
          <DevMenuOnboarding onClose={this.onOnboardingFinished} />
        )}

        <StyledView style={styles.separator} />

        <DevMenuButton buttonKey="reload" label="Reload" onPress={this.onAppReload} icon="reload" />
        {task && task.manifestUrl && (
          <DevMenuButton
            buttonKey="copy"
            label="Copy link to clipboard"
            onPress={this.onCopyTaskUrl}
            icon="clipboard-text"
          />
        )}
        <DevMenuButton buttonKey="home" label="Go to Home" onPress={this.onGoToHome} icon="home" />

        {this._maybeRenderDevMenuTools()}
        <DevMenuCloseButton onPress={this.collapseAndCloseDevMenuAsync} />
      </StyledView>
    );
  }

  _maybeRenderDevMenuTools() {
    const devMenuItems = Object.keys(this.state.devMenuItems).sort(
      (a, b) => DEV_MENU_ORDER.indexOf(a) - DEV_MENU_ORDER.indexOf(b)
    );

    if (this.state.enableDevMenuTools && this.state.devMenuItems) {
      return (
        <>
          <StyledView style={styles.separator} />
          {devMenuItems.map(key => {
            return this._renderDevMenuItem(key, this.state.devMenuItems[key]);
          })}
        </>
      );
    }
    return null;
  }

  _renderDevMenuItem(key, item) {
    const { label, isEnabled, detail } = item;

    return (
      <DevMenuButton
        key={key}
        buttonKey={key}
        label={label}
        onPress={this.onPressDevMenuButton}
        icon={MENU_ITEMS_ICON_MAPPINGS[key]}
        withSeparator={false}
        isEnabled={isEnabled}
        detail={detail}
      />
    );
  }

  _onOpenQRCode = async () => {
    if (await requestCameraPermissionsAsync()) {
      Kernel.selectQRReader();
    } else {
      alert('In order to use the QR Code scanner you need to provide camera permissions');
    }
  };
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 10,
    borderRadius: 20,
  },
  buttonContainer: {
    backgroundColor: 'transparent',
  },
  separator: {
    borderTopWidth: 1 / PixelRatio.get(),
    height: 12,
    marginVertical: 4,
    marginHorizontal: -1,
  },
});

export default DevMenuView;
