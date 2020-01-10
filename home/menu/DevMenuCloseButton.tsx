import React from 'react';
import { ThemeContext } from 'react-navigation';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { StyleSheet, TouchableHighlight } from 'react-native';

type Props = {
  onPress: () => void;
};

const HIT_SLOP = { top: 15, bottom: 15, left: 15, right: 15 };

class DevMenuCloseButton extends React.PureComponent<Props, any> {
  static contextType = ThemeContext;

  onPress = () => {
    if (this.props.onPress) {
      this.props.onPress();
    }
  };

  render() {
    return (
      <TouchableHighlight
        style={styles.closeButton}
        onPress={this.onPress}
        underlayColor={this.context === 'light' ? '#eee' : '#333'}
        hitSlop={HIT_SLOP}>
        <MaterialCommunityIcons
          name="close"
          size={20}
          color="#2F9BE4"
          style={styles.closeButtonIcon}
        />
      </TouchableHighlight>
    );
  }
}

const styles = StyleSheet.create({
  closeButton: {
    position: 'absolute',
    right: 12,
    top: 12,
    paddingVertical: 6,
    paddingHorizontal: 6,
    borderRadius: 2,
  },
  closeButtonIcon: {
    width: 20,
    height: 20,
  },
});

export default DevMenuCloseButton;
