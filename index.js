import React from 'react';
import { NativeModules, requireNativeComponent } from 'react-native';

const { Mediatags } = NativeModules;

export class Artwork extends React.Component {
  render() {
    return <RNTArtwork {...this.props} />
  }
}

const RNTArtwork = requireNativeComponent('RNTArtwork', Artwork);

export default Mediatags;
