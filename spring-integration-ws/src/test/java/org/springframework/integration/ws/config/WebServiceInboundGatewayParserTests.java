/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.ws.config;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import javax.xml.transform.Source;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.history.MessageHistory;
import org.springframework.integration.mapping.AbstractHeaderMapper;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.ws.MarshallingWebServiceInboundGateway;
import org.springframework.integration.ws.SimpleWebServiceInboundGateway;
import org.springframework.integration.ws.SoapHeaderMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.PollableChannel;
import org.springframework.oxm.Unmarshaller;
import org.springframework.oxm.support.AbstractMarshaller;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.ws.context.DefaultMessageContext;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;

/**
 * @author Iwein Fuld
 * @author Oleg Zhurakousky
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Stephane Nicoll
 * @author Artem Bilan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class WebServiceInboundGatewayParserTests {

	@Autowired
	@Qualifier("requestsMarshalling")
	PollableChannel requestsMarshalling;

	@Autowired
	@Qualifier("requestsSimple")
	PollableChannel requestsSimple;

	@Autowired
	@Qualifier("customErrorChannel")
	MessageChannel customErrorChannel;

	@Autowired
	@Qualifier("requestsVerySimple")
	MessageChannel requestsVerySimple;

	@Test
	public void configOk() throws Exception {
		// config valid
	}

	//Simple
	@Autowired
	@Qualifier("simple")
	SimpleWebServiceInboundGateway simpleGateway;

	@Test
	public void simpleGatewayProperties() throws Exception {
		assertSame(this.requestsVerySimple, TestUtils.getPropertyValue(this.simpleGateway, "requestChannel"));
		assertSame(this.customErrorChannel, TestUtils.getPropertyValue(this.simpleGateway, "errorChannel"));
		assertFalse(TestUtils.getPropertyValue(this.simpleGateway, "autoStartup", Boolean.class));
		assertEquals(101, TestUtils.getPropertyValue(this.simpleGateway, "phase"));
	}

	//extractPayload = false
	@Autowired
	@Qualifier("extractsPayload")
	SimpleWebServiceInboundGateway payloadExtractingGateway;

	@Test
	public void extractPayloadSet() throws Exception {
		DirectFieldAccessor accessor = new DirectFieldAccessor(
				payloadExtractingGateway);
		assertThat((Boolean) accessor.getPropertyValue("extractPayload"),
				is(false));
	}

	//marshalling
	@Autowired
	@Qualifier("marshalling")
	MarshallingWebServiceInboundGateway marshallingGateway;

	@Autowired
	AbstractMarshaller marshaller;

	@Test
	public void marshallersSet() throws Exception {
		DirectFieldAccessor accessor = new DirectFieldAccessor(marshallingGateway);

		AbstractMarshaller retrievedMarshaller = (AbstractMarshaller) accessor.getPropertyValue("marshaller");
		assertThat(retrievedMarshaller, is(marshaller));

		AbstractMarshaller retrievedUnMarshaller = (AbstractMarshaller) accessor.getPropertyValue("unmarshaller");
		assertThat(retrievedUnMarshaller, is(marshaller));

		assertTrue("messaging gateway is not running", marshallingGateway.isRunning());

		assertThat(
				(MessageChannel) accessor.getPropertyValue("errorChannel"),
				is(customErrorChannel));

		AbstractHeaderMapper.HeaderMatcher requestHeaderMatcher = TestUtils.getPropertyValue(marshallingGateway,
				"headerMapper.requestHeaderMatcher", AbstractHeaderMapper.HeaderMatcher.class);
		assertTrue(requestHeaderMatcher.matchHeader("testRequest"));
		assertFalse(requestHeaderMatcher.matchHeader("testReply"));

		AbstractHeaderMapper.HeaderMatcher replyHeaderMatcher = TestUtils.getPropertyValue(marshallingGateway,
				"headerMapper.replyHeaderMatcher", AbstractHeaderMapper.HeaderMatcher.class);
		assertFalse(replyHeaderMatcher.matchHeader("testRequest"));
		assertTrue(replyHeaderMatcher.matchHeader("testReply"));
	}

	@Test
	public void testMessageHistoryWithMarshallingGateway() throws Exception {
		MessageContext context = new DefaultMessageContext(new StubMessageFactory());
		Unmarshaller unmarshaller = mock(Unmarshaller.class);
		when(unmarshaller.unmarshal((Source) Mockito.any())).thenReturn("hello");
		marshallingGateway.setUnmarshaller(unmarshaller);
		marshallingGateway.invoke(context);
		Message<?> message = requestsMarshalling.receive(100);
		MessageHistory history = MessageHistory.read(message);
		assertNotNull(history);
		Properties componentHistoryRecord = TestUtils.locateComponentInHistory(history, "marshalling", 0);
		assertNotNull(componentHistoryRecord);
		assertEquals("ws:inbound-gateway", componentHistoryRecord.get("type"));
	}

	@Test
	public void testMessageHistoryWithSimpleGateway() throws Exception {
		MessageContext context = new DefaultMessageContext(new StubMessageFactory());
		payloadExtractingGateway.invoke(context);
		Message<?> message = requestsSimple.receive(100);
		MessageHistory history = MessageHistory.read(message);
		assertNotNull(history);
		Properties componentHistoryRecord = TestUtils.locateComponentInHistory(history, "extractsPayload", 0);
		assertNotNull(componentHistoryRecord);
		assertEquals("ws:inbound-gateway", componentHistoryRecord.get("type"));
	}

	@Autowired
	private SimpleWebServiceInboundGateway headerMappingGateway;

	@Autowired
	private SoapHeaderMapper testHeaderMapper;

	@Test
	public void testHeaderMapperReference() throws Exception {
		DirectFieldAccessor accessor = new DirectFieldAccessor(headerMappingGateway);
		Object headerMapper = accessor.getPropertyValue("headerMapper");
		assertEquals(testHeaderMapper, headerMapper);
	}

	@Autowired
	@Qualifier("replyTimeoutGateway")
	private SimpleWebServiceInboundGateway replyTimeoutGateway;

	@Test
	public void testReplyTimeout() throws Exception {
		DirectFieldAccessor accessor = new DirectFieldAccessor(replyTimeoutGateway);
		Object replyTimeout = accessor.getPropertyValue("replyTimeout");
		assertEquals(1234L, replyTimeout);
	}


	@SuppressWarnings("unused")
	private static class TestHeaderMapper implements SoapHeaderMapper {

		@Override
		public void fromHeadersToRequest(MessageHeaders headers,
		                                 SoapMessage target) {
		}

		@Override
		public void fromHeadersToReply(MessageHeaders headers, SoapMessage target) {
		}

		@Override
		public Map<String, Object> toHeadersFromRequest(SoapMessage source) {
			return Collections.emptyMap();
		}

		@Override
		public Map<String, Object> toHeadersFromReply(SoapMessage source) {
			return Collections.emptyMap();
		}
	}

}
