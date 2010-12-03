/*
 * Copyright (C) 2010 Sansar Choinyambuu
 * HSR Hochschule fuer Technik Rapperswil
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
 * @defgroup pb_assessment_result_message pb_assessment_result_message
 * @{ @ingroup tnccs_20
 */

#ifndef PB_ASSESSMENT_RESULT_MESSAGE_H_
#define PB_ASSESSMENT_RESULT_MESSAGE_H_

#include "pb_tnc_message.h"

typedef struct pb_assessment_result_message_t pb_assessment_result_message_t;

/**
 * Classs representing the PB-Assessment-Result message type.
 */
struct pb_assessment_result_message_t {

	/**
	 * PB-TNC Message interface
	 */
	pb_tnc_message_t pb_interface;

	/**
	 * Get PB Assessment result
	 *
	 * @return			PB Assessment result
	 */
	u_int32_t (*get_assessment_result)(pb_assessment_result_message_t *this);
};

/**
 * Create a PB-Assessment-Result message from parameters
 *
 * @param assessment_result		Assessment result code
 */
pb_tnc_message_t* pb_assessment_result_message_create(u_int32_t assessment_result);

/**
 * Create an unprocessed PB-Assessment-Result message from raw data
 *
  * @param data		PB-Assessment-Result message data
 */
pb_tnc_message_t* pb_assessment_result_message_create_from_data(chunk_t data);

#endif /** PB_PA_MESSAGE_H_ @}*/
