from pathlib import Path
from datetime import datetime
from typing import Optional
from cryptography.fernet import Fernet, InvalidToken
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
import base64
import time
import jwt
import requests
import typer
import yaml
import os
import sys
import subprocess


class DeviceCodeOAuth:
    """
    Class for authentication using OAuth Device Code grant
    """

    def __init__(
        self,
        perun_instance_name: str,
        client_id: str,
        scopes: str,
        metadata_url: str,
        encryption_password: str,
        use_mfa: bool,
        mfa_supported: bool,
        mfa_valid_minutes,
        debug: bool,
    ):
        """
        :param encryption_password: password for encrypting file with tokens
        :param use_mfa: flag for requiring Multi-Factor Authentication
        :param debug: flag for debugging output
        """
        self.perun_instance_name = perun_instance_name
        self.client_id = client_id
        self.scopes = scopes
        self.metadata_url = metadata_url
        self.password_bytes = bytes(encryption_password, "utf-8")
        self.use_mfa = use_mfa
        self.mfa_supported = mfa_supported
        self.mfa_valid_seconds = mfa_valid_minutes * 60
        self.debug = debug
        self.CLIENT_ID = client_id
        self.SCOPES = scopes
        metadata = requests.get(metadata_url).json()
        self.ISSUER = metadata["issuer"]
        self.DEVICE_AUTHORIZATION_ENDPOINT_URL = metadata[
            "device_authorization_endpoint"
        ]
        self.TOKEN_ENDPOINT_URL = metadata["token_endpoint"]
        self.USERINFO_ENDPOINT_URL = metadata["userinfo_endpoint"]
        self.REVOCATION_ENDPOINT_URL = metadata["revocation_endpoint"]
        self.pyJWKClient = jwt.PyJWKClient(metadata["jwks_uri"])
        self.tokens_path = self.__cache_dir() / "tokens"
        self.salt_path = self.__cache_dir() / "salt"
        self.tokens = self.__read_tokens_from_file()

    def get_access_token(self) -> str:
        """Provides valid access token with MFA if requested."""
        access_token = self.__get_valid_access_token()
        while self.use_mfa and not self.__verify_mfa():
            access_token = self.__login()
        return access_token

    def __get_valid_access_token(self) -> str:
        """Gets access token - tries to read it from file, get it using valid refresh token, or asks user to log in"""
        if self.tokens:
            access_token = self.tokens.get("access_token")
            if self.__verify_token(access_token, "access"):
                return access_token
            refresh_token = self.tokens.get("refresh_token")
            if self.__verify_token(refresh_token, "refresh"):
                return self.__refresh_tokens(refresh_token)
        return self.__login()

    def __cache_dir(self) -> Path:
        """
        Creates directory ~/.cache/perun/<instance> if it does not exist yet
        :return:
        """
        token_cache_dir = Path.home() / ".cache" / "perun" / self.perun_instance_name
        if not token_cache_dir.exists():
            if self.debug:
                print("creating directory ", token_cache_dir)
            token_cache_dir.mkdir(mode=0o700, parents=True)
        return token_cache_dir

    def __store_tokens(self, token_data: dict) -> None:
        """
        Stores tokens in the file ~/.cache/perun/tokens
        Encrypts the file if password is provided.
        :param token_data:
        """
        data = bytes(yaml.safe_dump(token_data), "utf-8")

        def opener(path, flags):
            return os.open(path, mode=0o700, flags=flags)

        if self.password_bytes:
            salt = os.urandom(16)
            kdf = PBKDF2HMAC(
                algorithm=hashes.SHA256(),
                length=32,
                salt=salt,
                iterations=390000,
            )
            key = base64.urlsafe_b64encode(kdf.derive(self.password_bytes))
            data = Fernet(key).encrypt(data)
            with open(self.salt_path, "wb", opener=opener) as f:
                f.write(salt)
        with open(self.tokens_path, "wb", opener=opener) as f:
            f.write(data)

    def __read_tokens_from_file(self) -> Optional[dict]:
        """
        Reads tokens from file.
        """
        try:
            with open(self.tokens_path, "rb") as f:
                data = f.read()
            if self.password_bytes:
                with open(self.salt_path, "rb") as f:
                    salt = f.read()
                kdf = PBKDF2HMAC(
                    algorithm=hashes.SHA256(), length=32, salt=salt, iterations=390000
                )
                key = base64.urlsafe_b64encode(kdf.derive(self.password_bytes))
                data = Fernet(key).decrypt(data)
            return yaml.safe_load(data)
        except (OSError, InvalidToken) as e:
            if self.debug:
                print("tokens not available in ", self.tokens_path)
                print(e)
            return None

    def __verify_token(self, token: str, token_type: str) -> bool:
        signing_key = self.pyJWKClient.get_signing_key_from_jwt(token)
        try:
            decoded_token = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256", "ES256"],
                audience=self.CLIENT_ID,
            )
            iss = decoded_token.get("iss")
            if iss != self.ISSUER:
                if self.debug:
                    print(
                        "issuer in stored",
                        token_type,
                        "token",
                        iss,
                        "does not match issuer from OIDC server",
                        self.ISSUER,
                    )
                return False
            if self.debug:
                print(token_type, "token verified")
                print(" issuer:", iss)
                if "iat" in decoded_token:
                    print(
                        " issued at: ",
                        datetime.fromtimestamp(decoded_token["iat"]).astimezone(),
                    )
                if "exp" in decoded_token:
                    print(
                        " expiration:",
                        datetime.fromtimestamp(decoded_token["exp"]).astimezone(),
                    )
                if "scope" in decoded_token:
                    print(" scope:", decoded_token["scope"])
                if "name" in decoded_token:
                    print(" name:", decoded_token["name"])
                if "acr" in decoded_token:
                    print(" acr:", decoded_token["acr"])
                if "auth_time" in decoded_token:
                    print(
                        " auth_time:",
                        datetime.fromtimestamp(decoded_token["auth_time"]).astimezone(),
                    )
            if self.use_mfa and token_type == "id":
                acr = decoded_token.get("acr")
                if acr is None or acr != "https://refeds.org/profile/mfa":
                    print(
                        "WARNING: MFA requested but not reported in id_token",
                        file=sys.stderr,
                    )
                    return False
            return True
        except jwt.ExpiredSignatureError:
            if self.debug:
                print(token_type, "token has expired")
        except jwt.InvalidAudienceError:
            if self.debug:
                print(token_type, "token is for another audience")
        except jwt.exceptions.InvalidSignatureError:
            if self.debug:
                print(token_type, "token is from other OIDC server")
        return False

    def __verify_mfa(self) -> bool:
        """
        Verifies MFA flag in id_token, and time of MFA from userInfo response
        :return: true if id_token contains acr:'https://refeds.org/profile/mfa'
        """
        id_token = self.tokens.get("id_token")
        try:
            decoded_id_token = jwt.decode(
                id_token,
                self.pyJWKClient.get_signing_key_from_jwt(id_token).key,
                algorithms=["RS256", "ES256"],
                audience=self.CLIENT_ID,
                options={"verify_signature": True, "verify_exp": False},
            )
            # get acr claim from id_token
            acr = decoded_id_token.get("acr")
            if acr is None:
                if self.debug:
                    print("id_token has no acr claim")
                return False
            # check that acr is MFA and not something else
            if acr != "https://refeds.org/profile/mfa":
                if self.debug:
                    print("MFA not detected, id_token has acr:", acr)
                return False
            # get time of authentication
            auth_time = decoded_id_token.get("auth_time")
            if auth_time is not None and self.debug:
                print(
                    "got auth_time from id_token:",
                    datetime.fromtimestamp(decoded_id_token["auth_time"]).astimezone(),
                )
            # check that time of MFA is not older than required
            if time.time() - auth_time > self.mfa_valid_seconds:
                if self.debug:
                    print(
                        "MFA is too old: ",
                        auth_time,
                        "max is",
                        self.mfa_valid_seconds,
                        "seconds",
                    )
                return False
            if self.debug:
                print("MFA verified")
            return True
        except jwt.InvalidAudienceError:
            if self.debug:
                print("id token is for another audience")
        except jwt.exceptions.InvalidSignatureError:
            if self.debug:
                print("id token is from other OIDC server")
        return False

    def __login(self) -> str:
        """
        Authenticate using OAUth 2.0 Device Code grant flow
        :return: access_token
        """
        if self.debug:
            print("doing AuthRequest")
        auth_request_params = {"client_id": self.CLIENT_ID, "scope": self.SCOPES}
        if self.use_mfa:
            if self.mfa_supported:
                auth_request_params["acr_values"] = "https://refeds.org/profile/mfa"
                auth_request_params["prompt"] = "login"
            else:
                print(
                    "WARNING: MFA requested but not supported by Perun instance",
                    file=sys.stderr,
                )
                raise typer.Exit(code=1)
        device_code_response = requests.post(
            self.DEVICE_AUTHORIZATION_ENDPOINT_URL, data=auth_request_params
        )
        if device_code_response.status_code != 200:
            print("Error generating the device code")
            raise typer.Exit(code=1)
        device_code_data = device_code_response.json()
        verification_url = device_code_data["verification_uri_complete"]
        if "&prompt=login" not in verification_url:
            verification_url = (
                device_code_data["verification_uri_complete"] + "&prompt=login"
            )
        print(
            "**************************************************************************************"
        )
        print("For authentication, navigate to: ", verification_url)
        print(
            "You can open the URL on your mobile phone by scanning the following QR code:"
        )
        subprocess.run(["qrencode", "-t", "UTF8", verification_url])
        print(
            "**************************************************************************************"
        )
        print()
        token_payload = {
            "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
            "device_code": device_code_data["device_code"],
            "client_id": self.CLIENT_ID,
        }
        while True:
            token_response = requests.post(self.TOKEN_ENDPOINT_URL, data=token_payload)
            token_data = token_response.json()
            if token_response.status_code == 200:
                if self.debug:
                    print("authenticated")
                return self.__store_token_data(token_data)
            elif token_data["error"] not in ("authorization_pending", "slow_down"):
                print(token_data["error_description"])
                raise typer.Exit(code=1)
            else:
                time.sleep(5)

    def __refresh_tokens(self, refresh_token: str) -> str:
        """
        Use refresh_token to get new access_token and refresh_token
        :return: access token
        """
        if self.debug:
            print("doing refresh")
        refresh_response = requests.post(
            self.TOKEN_ENDPOINT_URL,
            data={
                "grant_type": "refresh_token",
                "client_id": self.CLIENT_ID,
                "refresh_token": refresh_token,
            },
        )
        if refresh_response.status_code != 200:
            print("Error refreshing tokens")
            if self.debug:
                print(refresh_response.json())
            raise typer.Exit(code=1)
        return self.__store_token_data(refresh_response.json())

    def __store_token_data(self, token_data: dict) -> str:
        """
        Verifies all tokens and if verified, calls __store_tokens()
        :param token_data: data received from OAuth token endpoint
        :return: valid access token as string
        """
        id_token = token_data["id_token"]
        access_token = token_data["access_token"]
        refresh_token = token_data["refresh_token"]
        if (
            self.__verify_token(id_token, "id")
            and self.__verify_token(access_token, "access")
            and self.__verify_token(refresh_token, "refresh")
        ):
            self.tokens = token_data
            self.__store_tokens(token_data)
            return access_token
        else:
            print("ERROR: obtained tokens are not valid", file=sys.stderr)
            raise typer.Exit(code=1)
