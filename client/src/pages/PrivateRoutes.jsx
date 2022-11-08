import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useNavigate, Outlet, useLocation } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { setRefreshToken, getCookieToken } from './../utils/Cookie';
import { getInfo } from './../api/Users';
import { info } from './../store/User';

const PrivateRoutes = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const location = useLocation();

  const accessToken = localStorage.getItem('access-token');
  const refreshToken = getCookieToken();
  const [data, setData] = useState();

  const body = {
    accessToken,
    refreshToken,
  };

  useEffect(() => {
    try {
      getInfo(accessToken)
        .then((res) => {
          dispatch(info(res.data));
        })
        .catch((error) => {
          console.log(error.response.data.message);
          navigate('/signIn');
        });
    } catch (error) {
      axios({
        url: '/api/v1/refresh-token',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json;charset=UTF-8',
          Authorization: `Bearer ${accessToken}`,
        },
        data: JSON.stringify(body),
      })
        .then((response) => {
          setRefreshToken(response.data.refreshToken);
          localStorage.setItem('access-token', response.data.accessToken);
          navigate('/partyList');
        })
        .catch((error) => {
          console.log(error);
          console.log(error.response.data.message);
          navigate('/signIn');
        });
    }
  }, []);

  return (
    <>
      <Outlet />
    </>
  );
};

export default PrivateRoutes;
