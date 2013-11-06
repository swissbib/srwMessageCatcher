srwMessageCatcher
=================

SOAP Webservice based on the Apache Axis2 framework for catching SRW messages

in conjunction with the SRW pusher of CBS the srwMessageCatcher creates the communication channel between the swissbib DataHub layer and any Search Engine platform layer

compare the picture
https://github.com/swissbib/srwMessageCatcher/blob/master/notes/swissbib.service.overview.all.icons.no.header.png
as overview


Because the component uses a standard protocol (SRW) it is open and could be used by any content source - not only the swissbib DataHub but even by sources outside of the core swissbib service.
The caught messages are then processed in pipelines of the Search Engine document processing component and finally indexed by the Search Engine