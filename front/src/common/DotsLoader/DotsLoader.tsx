import React, { type FC } from 'react';

const DotLoader: FC<unknown> = () => (
  <div className="dots-loader">
    <div className="dots-loader--dot" />
    <div className="dots-loader--dot" />
    <div className="dots-loader--dot" />
  </div>
);

export default DotLoader;
