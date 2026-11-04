"""Sign a reviewed declarative filter package. Private keys are never part of portable sources."""
from pathlib import Path
import argparse
import base64
import json
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rules", type=Path, required=True)
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--signature", type=Path, required=True)
    parser.add_argument("--public-key", type=Path)
    parser.add_argument("--generate-key", action="store_true")
    args = parser.parse_args()
    payload = args.rules.read_bytes()
    if not 0 < len(payload) <= 1024 * 1024:
        raise ValueError("Rules exceed package size limit")
    package = json.loads(payload)
    if package.get("schemaVersion") != 1 or not isinstance(package.get("version"), int) or package["version"] < 1:
        raise ValueError("Unsupported schema/version")
    if args.generate_key:
        if args.key.exists():
            raise ValueError("Refusing to overwrite the filter-signing identity")
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        args.key.parent.mkdir(parents=True, exist_ok=True)
        args.key.write_bytes(key.private_bytes(serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    else:
        key = serialization.load_pem_private_key(args.key.read_bytes(), password=None)
    if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < 3072:
        raise ValueError("An RSA key of at least 3072 bits is required")
    signature = key.sign(payload, padding.PKCS1v15(), hashes.SHA256())
    args.signature.parent.mkdir(parents=True, exist_ok=True)
    args.signature.write_text(base64.b64encode(signature).decode("ascii") + "\n", encoding="ascii")
    if args.public_key:
        public = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        encoded = base64.b64encode(public).decode("ascii") + "\n"
        if args.public_key.exists() and args.public_key.read_text(encoding="ascii") != encoded:
            raise ValueError("Public key differs: do not rotate an installed trust anchor silently")
        args.public_key.parent.mkdir(parents=True, exist_ok=True)
        args.public_key.write_text(encoded, encoding="ascii")
    print("Signed reviewed filter package version", package["version"])


if __name__ == "__main__":
    main()
