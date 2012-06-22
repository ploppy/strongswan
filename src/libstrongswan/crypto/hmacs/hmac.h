/*
 * Copyright (C) 2012 Tobias Brunner
 * Copyright (C) 2005-2008 Martin Willi
 * Copyright (C) 2005 Jan Hutter
 * Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

/**
 * @defgroup hmac hmac
 * @{ @ingroup crypto
 */

#ifndef HMAC_H_
#define HMAC_H_

typedef struct hmac_t hmac_t;

#include <library.h>

/**
 * Generic interface for message authentication using hash functions.
 *
 * Classes implementing this interface can use the PRF and signer wrappers.
 */
struct hmac_t {

	/**
	 * Generate message authentication code.
	 *
	 * If out is NULL, no result is given back.  A next call will
	 * append the data to already supplied data.  If out is not NULL,
	 * the mac of all apended data is calculated, written to out and the
	 * internal state is reset.
	 *
	 * @param data		chunk of data to authenticate
	 * @param out		pointer where the generated bytes will be written
	 */
	void (*get_mac)(hmac_t *this, chunk_t data, u_int8_t *out);

	/**
	 * Get the size of the resulting MAC (i.e. the output size of the
	 * underlying hash function).
	 *
	 * @return			block size in bytes
	 */
	size_t (*get_mac_size)(hmac_t *this);

	/**
	 * Set the key to be used for the HMAC.
	 *
	 * Any key length must be accepted.
	 *
	 * @param key		key to set
	 */
	void (*set_key) (hmac_t *this, chunk_t key);

	/**
	 * Destroys a hmac_t object.
	 */
	void (*destroy) (hmac_t *this);
};

#endif /** HMAC_H_ @}*/