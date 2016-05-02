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

(def +onnistunut-urakan-kohdehakuvastaus+
  "<yha:urakan-kohdehakuvastaus xmlns:yha=\"http://www.liikennevirasto.fi/xsd/yha\">
    <yha:kohteet>
    <!--Zero or more repetitions:-->
      <yha:kohde>
        <yha:yha-id>3</yha:yha-id>
        <yha:kohdetyyppi>paikkaus</yha:kohdetyyppi>
        <yha:tunnus>string</yha:tunnus>
        <yha:yllapitoluokka>1</yha:yllapitoluokka>
        <yha:keskimaarainen-vuorokausiilikenne>1000</yha:keskimaarainen-vuorokausiilikenne>
        <yha:nykyinen-paallyste>1</yha:nykyinen-paallyste>
        <yha:tierekisteriosoitevali>
          <yha:karttapaivamaara>2016-01-01</yha:karttapaivamaara>
          <yha:tienumero>3</yha:tienumero>
          <yha:ajorata>0</yha:ajorata>
          <yha:kaista>11</yha:kaista>
          <yha:aosa>3</yha:aosa>
          <yha:aet>3</yha:aet>
          <yha:losa>3</yha:losa>
          <yha:let>3</yha:let>
        </yha:tierekisteriosoitevali>
        <yha:alikohteet>
          <yha:alikohde>
            <yha:yha-id>3</yha:yha-id>
            <yha:tierekisteriosoitevali>
              <yha:karttapaivamaara>2016-01-01</yha:karttapaivamaara>
              <yha:tienumero>3</yha:tienumero>
              <yha:ajorata>0</yha:ajorata>
              <yha:kaista>11</yha:kaista>
              <yha:aosa>3</yha:aosa>
              <yha:aet>3</yha:aet>
              <yha:losa>3</yha:losa>
              <yha:let>3</yha:let>
            </yha:tierekisteriosoitevali>
            <yha:tunnus>A</yha:tunnus>
            <yha:paallystystoimenpide>
              <yha:uusi-paallyste>11</yha:uusi-paallyste>
              <yha:raekoko>12</yha:raekoko>
              <yha:kokonaismassamaara>124</yha:kokonaismassamaara>
              <yha:rc-prosentti>14</yha:rc-prosentti>
              <yha:kuulamylly>4</yha:kuulamylly>
             <yha:paallystetyomenetelma>22</yha:paallystetyomenetelma>
          </yha:paallystystoimenpide>
        </yha:alikohde>
        </yha:alikohteet>
      </yha:kohde>
    </yha:kohteet>
  </yha:urakan-kohdehakuvastaus>")
