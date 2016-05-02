(ns harja.palvelin.integraatiot.yha.tyokalut)

(def +yha-url+ "http://localhost:1234")

(def +onnistunut-urakoiden-hakuvastaus+
  "<yha:urakoiden-hakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
    <yha:urakat>
      <yha:urakka>
       <yha:yha-id>3</yha:yha-id>
       <yha:elyt>
         <yha:ely>POP</yha:ely>
       </yha:elyt>
       <yha:vuodet>
         <yha:vuosi>2016</yha:vuosi>
       </yha:vuodet>
       <yha:sampotunnus>SAMPOTUNNUS</yha:sampotunnus>
       <yha:tunnus>YHATUNNUS</yha:tunnus>
      </yha:urakka>
    </yha:urakat>
  </yha:urakoiden-hakuvastaus>")

(def +urakkahaun-virhevastaus+
  "<yha:urakoiden-hakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
    <yha:urakat/>
    <yha:virhe>Tapahtui virhe</yha:virhe>
  </yha:urakoiden-hakuvastaus>")

(def +urakoiden-tyhja-hakuvastaus+
  "<yha:urakoiden-hakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
    <yha:urakat/>
  </yha:urakoiden-hakuvastaus>")

(def +invalidi-urakoiden-hakuvastaus+
  "<yha:urakoiden-hakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
    <yha:tagi-jonka-ei-kuulunut-elaa/>
    <yha:urakat/>
    <yha:virhe>Tapahtui virhe</yha:virhe>
  </yha:urakoiden-hakuvastaus>")

(def +usean-urakan-hakuvastaus+
  "<yha:urakoiden-hakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
    <yha:urakat>
      <yha:urakka>
       <yha:yha-id>3</yha:yha-id>
       <yha:elyt>
         <yha:ely>POP</yha:ely>
       </yha:elyt>
       <yha:vuodet>
         <yha:vuosi>2016</yha:vuosi>
       </yha:vuodet>
       <yha:sampotunnus>SAMPOTUNNUS1</yha:sampotunnus>
       <yha:tunnus>YHATUNNUS1</yha:tunnus>
      </yha:urakka>
      <yha:urakka>
        <yha:yha-id>3</yha:yha-id>
        <yha:elyt>
          <yha:ely>POP</yha:ely>
          <yha:ely>Pohjois-Savo</yha:ely>
        </yha:elyt>
        <yha:vuodet>
          <yha:vuosi>2016</yha:vuosi>
          <yha:vuosi>2017</yha:vuosi>
        </yha:vuodet>
        <yha:sampotunnus>SAMPOTUNNUS2</yha:sampotunnus>
        <yha:tunnus>YHATUNNUS2</yha:tunnus>
      </yha:urakka>
    </yha:urakat>
  </yha:urakoiden-hakuvastaus>")
