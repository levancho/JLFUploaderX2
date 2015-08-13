package com.github.levancho.jsuplaoder.largefile.utils;


import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

import com.github.levancho.jsuplaoder.largefile.logic.UploadServletAsyncProcessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import com.github.levancho.jsuplaoder.largefile.json.CRCResult;


/**
 * Helper providing methods to compute crc hash.
 * 
 * @see #getBufferedCrc(java.io.InputStream)
 * @author antoinem
 * 
 */
@Component
public class CRCHelper {


	private static Log log = LogFactory.getLog(CRCHelper.class);


	/**
	 * Returns a {@link CRCResult} computed with {@link java.util.zip.CRC32} from the stream specified as
	 * parameter.
	 * 
	 * @param inputStream
	 * @return {@link CRCResult}
	 * @throws java.io.IOException
	 */
	public CRCResult getBufferedCrc(InputStream inputStream)
			throws IOException {

		byte[] b = new byte[UploadServletAsyncProcessor.SIZE_OF_THE_BUFFER_IN_BYTES];
		int read;
		int totalRead = 0;
		CRC32 crc32 = new CRC32();
		while ((read = inputStream.read(b)) != -1) {
			crc32.update(b, 0, read);
			totalRead += read;
		}
		inputStream.close();

		CRCResult crcResult = new CRCResult();
		crcResult.setCrcAsString(Long.toHexString(crc32.getValue()));
		crcResult.setTotalRead(totalRead);

		log.debug("obtained crc for stream with length " + totalRead + " : " + crcResult.getCrcAsString());

		return crcResult;

	}
}
