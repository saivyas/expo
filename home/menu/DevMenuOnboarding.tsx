import React from 'react';
import Constants from 'expo-constants';
import { Dimensions, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { StyledText } from '../components/Text';

type Props = {
  onClose?: () => any;
};

const MENU_NARROW_SCREEN = Dimensions.get('window').width < 375;
const ONBOARDING_MESSAGE = (() => {
  const fragment = Constants.isDevice
    ? 'you can shake your device'
    : 'in an iOS Simulator you can press \u2318D';
  return `Since this is your first time opening the Expo client, we wanted to show you this menu and let you know that ${fragment} to get back to it at any time.`;
})();

class DevMenuOnboarding extends React.PureComponent<Props, {}> {
  onPress = () => {
    if (this.props.onClose) {
      this.props.onClose();
    }
  };

  render() {
    const headingStyles = MENU_NARROW_SCREEN
      ? [styles.onboardingHeading, styles.onboardingHeadingNarrow]
      : styles.onboardingHeading;

    return (
      <View style={styles.onboardingContainer}>
        <View style={styles.onboardingHeadingRow}>
          <StyledText style={headingStyles} lightColor="#595c68">
            Hello there, friend! 👋
          </StyledText>
        </View>
        <StyledText style={styles.onboardingTooltip} lightColor="#595c68">
          {ONBOARDING_MESSAGE}
        </StyledText>
        <TouchableOpacity style={styles.onboardingButton} onPress={this.onPress}>
          <Text style={styles.onboardingButtonLabel}>Got it</Text>
        </TouchableOpacity>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  onboardingContainer: {
    paddingHorizontal: 12,
  },
  onboardingHeadingRow: {
    flexDirection: 'row',
    marginTop: 16,
    marginRight: 16,
    marginBottom: 8,
  },
  onboardingHeading: {
    flex: 1,
    fontWeight: '700',
    fontSize: 22,
  },
  onboardingHeadingNarrow: {
    fontSize: 18,
    marginTop: 2,
  },
  onboardingTooltip: {
    marginRight: 16,
    marginVertical: 4,
    fontSize: 16,
  },
  onboardingButton: {
    alignItems: 'center',
    marginVertical: 12,
    paddingVertical: 10,
    backgroundColor: '#056ecf',
    borderRadius: 3,
  },
  onboardingButtonLabel: {
    color: '#fff',
    fontSize: 16,
  },
});

export default DevMenuOnboarding;
