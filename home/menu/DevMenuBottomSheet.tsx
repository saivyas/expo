import React from 'react';
import BottomSheet from 'reanimated-bottom-sheet';
import { EventSubscription, StyleSheet, View } from 'react-native';

import * as DevMenu from './DevMenuModule';
import DevMenuBottomSheetContext from './DevMenuBottomSheetContext';

type Props = {
  uuid: string;
};

class DevMenuBottomSheet extends React.PureComponent<Props, {}> {
  ref = React.createRef<BottomSheet>();

  snapPoints = [0, '60%'];

  closeStarted = false;

  closeSubscription: EventSubscription | null = null;

  componentDidMount() {
    this.expand();

    // Before the dev menu can be actually closed, we need to collapse its sheet view,
    // and this listens for close requests that come from native side to start collapsing the view.
    // The awaited return value of this listener is then send back as a response
    // so the native module knows when it can fully close dev menu (detach its root view).
    this.closeSubscription = DevMenu.listenForCloseRequests(() => this.collapse());
  }

  componentDidUpdate(prevProps) {
    // Make sure it gets expanded once we receive new identifier.
    if (prevProps.uuid !== this.props.uuid) {
      this.closeStarted = false;
      this.expand();
    }
  }

  componentWillUnmount() {
    if (this.closeSubscription) {
      this.closeSubscription.remove();
      this.closeSubscription = null;
    }
  }

  collapse = (): Promise<void> => {
    this.ref.current && this.ref.current.snapTo(0);

    // Use setTimeout until there is a better solution to execute something once the sheet is fully collapsed.
    return new Promise(resolve => setTimeout(resolve, 200));
  };

  expand = () => {
    this.ref.current && this.ref.current.snapTo(1);
  };

  onCloseStart = () => {
    this.closeStarted = true;
  };

  onCloseEnd = async () => {
    if (this.closeStarted) {
      this.closeStarted = false;
      await this.collapse();
      await DevMenu.closeAsync();
    }
  };

  providedContext = {
    expand: this.expand,
    collapse: this.collapse,
  };

  renderContent = () => {
    return <View style={styles.bottomSheetContent}>{this.props.children}</View>;
  };

  render() {
    return (
      <DevMenuBottomSheetContext.Provider value={this.providedContext}>
        <View style={styles.bottomSheetContainer}>
          <BottomSheet
            ref={this.ref}
            initialSnap={0}
            snapPoints={this.snapPoints}
            renderContent={this.renderContent}
            onCloseStart={this.onCloseStart}
            onCloseEnd={this.onCloseEnd}
          />
        </View>
      </DevMenuBottomSheetContext.Provider>
    );
  }
}

const styles = StyleSheet.create({
  bottomSheetContainer: {
    flex: 1,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 30,
  },
  bottomSheetContent: {
    minHeight: '100%',
  },
});

export default DevMenuBottomSheet;
