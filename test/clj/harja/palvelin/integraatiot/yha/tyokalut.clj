(ns harja.palvelin.integraatiot.yha.tyokalut)

(def +yha-url+ "http://localhost:1234/")

(def +onnistunut-urakoiden-hakuvastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
   <urakoiden-hakuvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
    <urakat>
      <urakka>
       <yha-id>3</yha-id>
       <elyt>
         <ely>POP</ely>
       </elyt>
       <vuodet>
         <vuosi>2016</vuosi>
       </vuodet>
       <sampotunnus>SAMPOTUNNUS</sampotunnus>
       <tunnus>YHATUNNUS</tunnus>
      </urakka>
    </urakat>
  </urakoiden-hakuvastaus>")

(def +virhevastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
  <urakoiden-hakuvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
    <urakat/>
    <virhe>Tapahtui virhe</virhe>
  </urakoiden-hakuvastaus>")

(def +urakoiden-tyhja-hakuvastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
  <urakoiden-hakuvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
    <urakat/>
  </urakoiden-hakuvastaus>")

(def +invalidi-urakoiden-hakuvastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
  <urakoiden-hakuvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
    <tagi-jonka-ei-kuulunut-elaa/>
    <urakat/>
    <virhe>Tapahtui virhe</virhe>
  </urakoiden-hakuvastaus>")

(def +usean-urakan-hakuvastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
  <urakoiden-hakuvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
    <urakat>
      <urakka>
       <yha-id>3</yha-id>
       <elyt>
         <ely>POP</ely>
       </elyt>
       <vuodet>
         <vuosi>2016</vuosi>
       </vuodet>
       <sampotunnus>SAMPOTUNNUS1</sampotunnus>
       <tunnus>YHATUNNUS1</tunnus>
      </urakka>
      <urakka>
        <yha-id>3</yha-id>
        <elyt>
          <ely>POP</ely>
          <ely>Pohjois-Savo</ely>
        </elyt>
        <vuodet>
          <vuosi>2016</vuosi>
          <vuosi>2017</vuosi>
        </vuodet>
        <sampotunnus>SAMPOTUNNUS2</sampotunnus>
        <tunnus>YHATUNNUS2</tunnus>
      </urakka>
    </urakat>
  </urakoiden-hakuvastaus>")

(def +onnistunut-urakan-kohdehakuvastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
  <urakan-kohdehakuvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
    <kohteet>
      <kohde>
        <yha-id>3</yha-id>
        <kohdenumero>666</kohdenumero>
        <kohdetyyppi>1</kohdetyyppi>
        <kohdetyotyyppi>paikkaus</kohdetyotyyppi>
        <nimi>string</nimi>
        <tunnus>A</tunnus>
        <tierekisteriosoitevali>
          <karttapaivamaara>2016-01-01</karttapaivamaara>
          <tienumero>3</tienumero>
          <aosa>3</aosa>
          <aet>3</aet>
          <losa>3</losa>
          <let>3</let>
        </tierekisteriosoitevali>
        <alikohteet>
          <alikohde>
            <yha-id>3</yha-id>
            <tierekisteriosoitevali>
              <karttapaivamaara>2016-01-01</karttapaivamaara>
              <tienumero>3</tienumero>
              <aosa>3</aosa>
              <aet>3</aet>
              <losa>3</losa>
              <let>3</let>
              <ajorata>0</ajorata>
              <kaista>11</kaista>
            </tierekisteriosoitevali>
            <paallystystoimenpide>
              <uusi-paallyste>11</uusi-paallyste>
              <raekoko>12</raekoko>
              <kokonaismassamaara>124</kokonaismassamaara>
              <rc-prosentti>14</rc-prosentti>
              <kuulamylly>4</kuulamylly>
              <paallystetyomenetelma>22</paallystetyomenetelma>
            </paallystystoimenpide>
            <keskimaarainen-vuorokausiliikenne>1000</keskimaarainen-vuorokausiliikenne>
            <yllapitoluokka>1</yllapitoluokka>
            <nykyinen-paallyste>1</nykyinen-paallyste>
          </alikohde>
          <alikohde>
            <yha-id>4</yha-id>
            <tierekisteriosoitevali>
              <karttapaivamaara>2016-01-01</karttapaivamaara>
              <tienumero>3</tienumero>
              <aosa>3</aosa>
              <aet>3</aet>
              <losa>3</losa>
              <let>3</let>
              <ajorata>0</ajorata>
              <kaista>11</kaista>
            </tierekisteriosoitevali>
            <paallystystoimenpide>
              <uusi-paallyste>11</uusi-paallyste>
              <raekoko>12</raekoko>
              <kokonaismassamaara>124</kokonaismassamaara>
              <rc-prosentti>14</rc-prosentti>
              <kuulamylly>4</kuulamylly>
              <paallystetyomenetelma>22</paallystetyomenetelma>
            </paallystystoimenpide>
            <keskimaarainen-vuorokausiliikenne>1000</keskimaarainen-vuorokausiliikenne>
            <yllapitoluokka>1</yllapitoluokka>
            <nykyinen-paallyste>1</nykyinen-paallyste>
          </alikohde>
        </alikohteet>
      </kohde>
    </kohteet>
  </urakan-kohdehakuvastaus>")


(def +urakan-kohdehakuvastaus-jossa-toisen-urakan-sidottu-kohde+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
  <urakan-kohdehakuvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
    <kohteet>
      <kohde>
        <yha-id>3</yha-id>
        <kohdenumero>666</kohdenumero>
        <kohdetyyppi>1</kohdetyyppi>
        <kohdetyotyyppi>paikkaus</kohdetyotyyppi>
        <nimi>string</nimi>
        <tunnus>A</tunnus>
        <tierekisteriosoitevali>
          <karttapaivamaara>2016-01-01</karttapaivamaara>
          <tienumero>3</tienumero>
          <aosa>3</aosa>
          <aet>3</aet>
          <losa>3</losa>
          <let>3</let>
        </tierekisteriosoitevali>
        <alikohteet>
          <alikohde>
            <yha-id>3</yha-id>
            <tierekisteriosoitevali>
              <karttapaivamaara>2016-01-01</karttapaivamaara>
              <tienumero>3</tienumero>
              <aosa>3</aosa>
              <aet>3</aet>
              <losa>3</losa>
              <let>3</let>
              <ajorata>0</ajorata>
              <kaista>11</kaista>
            </tierekisteriosoitevali>
            <paallystystoimenpide>
              <uusi-paallyste>11</uusi-paallyste>
              <raekoko>12</raekoko>
              <kokonaismassamaara>124</kokonaismassamaara>
              <rc-prosentti>14</rc-prosentti>
              <kuulamylly>4</kuulamylly>
              <paallystetyomenetelma>22</paallystetyomenetelma>
            </paallystystoimenpide>
            <keskimaarainen-vuorokausiliikenne>1000</keskimaarainen-vuorokausiliikenne>
            <yllapitoluokka>1</yllapitoluokka>
            <nykyinen-paallyste>1</nykyinen-paallyste>
          </alikohde>
          <alikohde>
            <yha-id>4</yha-id>
            <tierekisteriosoitevali>
              <karttapaivamaara>2016-01-01</karttapaivamaara>
              <tienumero>3</tienumero>
              <aosa>3</aosa>
              <aet>3</aet>
              <losa>3</losa>
              <let>3</let>
              <ajorata>0</ajorata>
              <kaista>11</kaista>
            </tierekisteriosoitevali>
            <paallystystoimenpide>
              <uusi-paallyste>11</uusi-paallyste>
              <raekoko>12</raekoko>
              <kokonaismassamaara>124</kokonaismassamaara>
              <rc-prosentti>14</rc-prosentti>
              <kuulamylly>4</kuulamylly>
              <paallystetyomenetelma>22</paallystetyomenetelma>
            </paallystystoimenpide>
            <keskimaarainen-vuorokausiliikenne>1000</keskimaarainen-vuorokausiliikenne>
            <yllapitoluokka>1</yllapitoluokka>
            <nykyinen-paallyste>1</nykyinen-paallyste>
          </alikohde>
        </alikohteet>
      </kohde>
      <kohde>
        <yha-id>52400</yha-id>
        <kohdenumero>111</kohdenumero>
        <kohdetyyppi>1</kohdetyyppi>
        <kohdetyotyyppi>paikkaus</kohdetyotyyppi>
        <nimi>string</nimi>
        <tunnus>A</tunnus>
        <tierekisteriosoitevali>
          <karttapaivamaara>2016-01-01</karttapaivamaara>
          <tienumero>3</tienumero>
          <aosa>3</aosa>
          <aet>3</aet>
          <losa>3</losa>
          <let>3</let>
        </tierekisteriosoitevali>
        <alikohteet>
          <alikohde>
            <yha-id>52401</yha-id>
            <tierekisteriosoitevali>
              <karttapaivamaara>2016-01-01</karttapaivamaara>
              <tienumero>3</tienumero>
              <aosa>3</aosa>
              <aet>3</aet>
              <losa>3</losa>
              <let>3</let>
              <ajorata>0</ajorata>
              <kaista>11</kaista>
            </tierekisteriosoitevali>
            <paallystystoimenpide>
              <uusi-paallyste>11</uusi-paallyste>
              <raekoko>12</raekoko>
              <kokonaismassamaara>124</kokonaismassamaara>
              <rc-prosentti>14</rc-prosentti>
              <kuulamylly>4</kuulamylly>
              <paallystetyomenetelma>22</paallystetyomenetelma>
            </paallystystoimenpide>
            <keskimaarainen-vuorokausiliikenne>1000</keskimaarainen-vuorokausiliikenne>
            <yllapitoluokka>1</yllapitoluokka>
            <nykyinen-paallyste>1</nykyinen-paallyste>
          </alikohde>
          <alikohde>
            <yha-id>42502</yha-id>
            <tierekisteriosoitevali>
              <karttapaivamaara>2016-01-01</karttapaivamaara>
              <tienumero>3</tienumero>
              <aosa>3</aosa>
              <aet>3</aet>
              <losa>3</losa>
              <let>3</let>
              <ajorata>0</ajorata>
              <kaista>11</kaista>
            </tierekisteriosoitevali>
            <paallystystoimenpide>
              <uusi-paallyste>11</uusi-paallyste>
              <raekoko>12</raekoko>
              <kokonaismassamaara>124</kokonaismassamaara>
              <rc-prosentti>14</rc-prosentti>
              <kuulamylly>4</kuulamylly>
              <paallystetyomenetelma>22</paallystetyomenetelma>
            </paallystystoimenpide>
            <keskimaarainen-vuorokausiliikenne>1000</keskimaarainen-vuorokausiliikenne>
            <yllapitoluokka>1</yllapitoluokka>
            <nykyinen-paallyste>1</nykyinen-paallyste>
          </alikohde>
        </alikohteet>
      </kohde>
    </kohteet>
  </urakan-kohdehakuvastaus>")

(def +invalidi-urakan-kohdehakuvastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
  <urakan-kohdehakuvastaus xmlns:yha=\"http://www.vayla.fi/xsd/yha\">
    <tagi-jonka-ei-kuulunut-elaa/>
    <kohteet/>
    <virhe>Tapahtui virhe</virhe>
  </urakan-kohdehakuvastaus>")

(def +tyhja-urakan-kohteidenhakuvastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
  <urakan-kohdehakuvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
    <kohteet/>
  </urakan-kohdehakuvastaus>")

(defn +epaonnistunut-kohteen-poisto-vastaus+ [yha-kohde-id]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
        <urakan-kohteen-poiston-kirjausvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
        <palaute>palaute</palaute>
        <virhe>
            <kohde-yha-id>" yha-kohde-id "</kohde-yha-id>
            <selite>virhe</selite>
        </virhe>
        </urakan-kohteen-poiston-kirjausvastaus>"))

(def +onnistunut-kohteen-poisto-vastaus+
  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
   <urakan-kohteen-poiston-kirjausvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">
   <palaute>palaute</palaute>
   </urakan-kohteen-poiston-kirjausvastaus>")
