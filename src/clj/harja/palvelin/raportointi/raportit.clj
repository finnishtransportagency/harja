(ns harja.palvelin.raportointi.raportit
  "Sisältää kaikki Harjan raportit. Nämä tiedot ennen ladattiin tietokannasta,
  nyt ne on määritelty kätevästi `raportit` vektorissa.

  Jos lisäät uuden raportin joudut tekemään kolme toimenpidettä:
  - lisää sille oikeudet roolit -exceliin
  - lisää sen nimiavaruus require alle
  - lisää raportin tiedot, konteksti ja parametrit `raportit` vektoriin."

 (:require
  ;; vaaditaan built in raportit
  [harja.palvelin.raportointi.raportit.erilliskustannukset]
  [harja.palvelin.raportointi.raportit.ilmoitus]
  [harja.palvelin.raportointi.raportit.laskutusyhteenveto]
  [harja.palvelin.raportointi.raportit.laskutusyhteenveto-tuotekohtainen]
  [harja.palvelin.raportointi.raportit.laskutusyhteenveto-tyomaa]
  [harja.palvelin.raportointi.raportit.tyomaapaivakirja]
  [harja.palvelin.raportointi.raportit.ilmoitukset]
  [harja.palvelin.raportointi.raportit.tehtavamaarat]
  [harja.palvelin.raportointi.raportit.vemtr]
  [harja.palvelin.raportointi.raportit.kulut-tehtavaryhmittain]
  [harja.palvelin.raportointi.raportit.materiaali]
  [harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti]
  [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain]
  [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain]
  [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain]
  [harja.palvelin.raportointi.raportit.suolasakko]
  [harja.palvelin.raportointi.raportit.tiestotarkastus]
  [harja.palvelin.raportointi.raportit.kelitarkastus]
  [harja.palvelin.raportointi.raportit.laaduntarkastus]
  [harja.palvelin.raportointi.raportit.laatupoikkeama]
  [harja.palvelin.raportointi.raportit.siltatarkastus]
  [harja.palvelin.raportointi.raportit.sanktio]
  [harja.palvelin.raportointi.raportit.sanktioraportti-yllapito]
  [harja.palvelin.raportointi.raportit.soratietarkastus]
  [harja.palvelin.raportointi.raportit.valitavoiteraportti]
  [harja.palvelin.raportointi.raportit.ymparisto]
  [harja.palvelin.raportointi.raportit.tyomaakokous]
  [harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat]
  [harja.palvelin.raportointi.raportit.toimenpideajat]
  [harja.palvelin.raportointi.raportit.toimenpidepaivat]
  [harja.palvelin.raportointi.raportit.toimenpidekilometrit]
  [harja.palvelin.raportointi.raportit.indeksitarkistus]
  [harja.palvelin.raportointi.raportit.lisatyo]
  [harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto]
  [harja.palvelin.raportointi.raportit.vesivaylien-laskutusyhteenveto]
  [harja.palvelin.raportointi.raportit.kanavien-laskutusyhteenveto]
  [harja.palvelin.raportointi.raportit.kanavien-hairiotilanteet]
  [harja.palvelin.raportointi.raportit.yllapidon-aikataulu]
  [harja.palvelin.raportointi.raportit.vastaanottotarkastus]
  [harja.palvelin.raportointi.raportit.kanavien-muutos-ja-lisatyot]
  [harja.palvelin.raportointi.raportit.kanavien-liikennetapahtumat]
  [harja.palvelin.raportointi.raportit.kanavien-toimenpiteet]
  [harja.palvelin.raportointi.raportit.pohjavesialueiden-suolat]
  [harja.palvelin.raportointi.raportit.rajoitusalueiden-suolat]
  [harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara]
  [harja.palvelin.raportointi.raportit.paikkausten-yhteenveto]
  [harja.palvelin.raportointi.raportit.paikkausten-yhteenveto-mhu]
  [harja.palvelin.raportointi.raportit.tiemerkinta-kustannukset]
  [harja.domain.urakka :as urakka-domain]
  [clojure.set :as set]))

;; HOX Muista lisätä uusi raportti myös Roolit-Exceliin!

(def raportit
  [{:nimi         :ilmoitukset-raportti
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :kuvaus       "Ilmoitukset"
    :suorita      #'harja.palvelin.raportointi.raportit.ilmoitukset/suorita}

   {:nimi         :sanktioraportti-yllapito
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Sakko- ja bonusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.sanktioraportti-yllapito/suorita
    :urakkatyyppi #{:paallystys :paikkaus :tiemerkinta}}

   {:nimi         :soratietarkastusraportti
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "tienumero", :konteksti nil, :pakollinen false, :nimi "Tienumero"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Soratietarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.soratietarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :laskutusyhteenveto
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "urakka"}
    :kuvaus       "Laskutusyhteenveto"
    :suorita      #'harja.palvelin.raportointi.raportit.laskutusyhteenveto/suorita
    :urakkatyyppi #{:hoito}}

   {:nimi         :laskutusyhteenveto-tuotekohtainen
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "urakka"}
    :kuvaus       "Laskutusyhteenveto"
    :kuvaus-tarkenne "Laskutusyhteenveto (tuotekohtainen)"
    :suorita      #'harja.palvelin.raportointi.raportit.laskutusyhteenveto-tuotekohtainen/suorita
    :urakkatyyppi #{:teiden-hoito}}

   {:nimi         :laskutusyhteenveto-tyomaa
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "urakka"}
    :kuvaus       "Laskutusyhteenveto"
    :kuvaus-tarkenne "Laskutusyhteenveto (työmaakokous)"
    :suorita      #'harja.palvelin.raportointi.raportit.laskutusyhteenveto-tyomaa/suorita
    :urakkatyyppi #{:teiden-hoito}}

   {:nimi         :tyomaapaivakirja-nakyma
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :kuvaus       "Työmaapäiväkirja"
    :suorita      #'harja.palvelin.raportointi.raportit.tyomaapaivakirja/suorita}

   {:nimi :tehtavamaarat
    :parametrit [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus "Tehtävämäärät"
    :suorita #'harja.palvelin.raportointi.raportit.tehtavamaarat/suorita
    :urakkatyyppi #{:teiden-hoito}}

   {:nimi :vemtr
    :parametrit [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                 {:tyyppi "checkbox" :konteksti nil, :pakollinen false :nimi "Vain MHUt ja HJU:t"}]
    :konteksti #{"elinvoimakeskus" "koko maa"}
    :kuvaus "Valtakunnalliset ja ELY-kohtaiset määrätoteumat"
    :suorita #'harja.palvelin.raportointi.raportit.vemtr/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :laaduntarkastusraportti
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "tienumero", :konteksti nil, :pakollinen false, :nimi "Tienumero"}
                   {:tyyppi "checkbox", :konteksti nil, :pakollinen false, :nimi "Vain laadun alitukset"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Laaduntarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.laaduntarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :sanktioraportti
    :kuvaus-tarkenne "Sanktiot, bonukset ja arvonvähennykset"
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Sanktioiden yhteenveto"
    :suorita      #'harja.palvelin.raportointi.raportit.sanktio/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :kelitarkastusraportti
    :parametrit   [{:tyyppi "tienumero", :konteksti nil, :pakollinen false, :nimi "Tienumero"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Kelitarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.kelitarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :tiestotarkastusraportti
    :rajoita-pdf-rivimaara  30000
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "tienumero", :konteksti nil, :pakollinen false, :nimi "Tienumero"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Tiestötarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.tiestotarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :ymparistoraportti
    :parametrit   [{:tyyppi "urakoittain", :konteksti nil, :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "urakkanumero", :konteksti nil, :pakollinen true, :nimi "Näytä urakkanumerot"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Ympäristöraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.ymparisto/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :turvallisuus
    :parametrit   [{:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "elinvoimakeskus", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Turvallisuusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat/suorita
    :urakkatyyppi (set/union #{:hoito :teiden-hoito :paallystys :paikkaus :tiemerkinta} urakka-domain/vesivayla-urakkatyypit)}

   {:nimi             :yks-hint-kuukausiraportti
    :parametrit       [{:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                      {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                      {:tyyppi "urakoittain", :konteksti "elinvoimakeskus", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                      {:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                      {:tyyppi "urakan-toimenpide", :konteksti nil, :pakollinen false, :nimi "Toimenpide"}]
    :konteksti        #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus           "Yksikköhintaiset työt kuukausittain"
    :kuvaus-tarkenne  "Yksikköhintaiset työt kuukausittain (au jv)"
    :suorita          #'harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain/suorita
    :urakkatyyppi     #{:hoito}}

   {:nimi             :materiaaliraportti
    :parametrit       [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti        #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus           "Materiaaliraportti"
    :kuvaus-tarkenne  "Materiaaliraportti (jv)"
    :suorita          #'harja.palvelin.raportointi.raportit.materiaali/suorita
    :urakkatyyppi     #{:hoito :teiden-hoito}}

   {:nimi         :lisatyo
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Lisätyöraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.lisatyo/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :laatupoikkeamaraportti
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "laatupoikkeamatekija", :konteksti nil, :pakollinen true, :nimi "Laatupoikkeamatekija"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Laatupoikkeamaraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.laatupoikkeama/suorita
    :urakkatyyppi (set/union #{:hoito :teiden-hoito :paallystys :paikkaus :tiemerkinta} urakka-domain/vesivayla-urakkatyypit)}

   {:nimi             :yks-hint-tehtavien-summat
    :parametrit       [{:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                       {:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                       {:tyyppi "urakan-toimenpide", :konteksti nil, :pakollinen false, :nimi "Toimenpide"}
                       {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                       {:tyyppi "urakoittain", :konteksti "elinvoimakeskus", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}]
    :konteksti        #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus           "Yksikköhintaiset työt tehtävittäin"
    :kuvaus-tarkenne  "Yksikköhintaiset työt tehtävittäin (au jv)"
    :suorita          #'harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain/suorita
    :urakkatyyppi     #{:hoito}}

   {:nimi         :tyomaakokous
    :parametrit   [{:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Sanktioiden yhteenveto" :oletusarvo true}
                   ;;    {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Muutos- ja lisätyöraportti" :oletusarvo true} Jätetään tässä vaiheessa pois, koska muutos- ja lisätyöraportti toinmii vain hoitovuoikohtaisesti
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Laskutusyhteenveto" :oletusarvo true}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Laatupoikkeamat" :oletusarvo true}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Ympäristöraportti" :oletusarvo true}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Tehtävämäärät" :oletusarvo true}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Välitavoitteet" :oletusarvo true}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Soratietarkastukset"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Laaduntarkastusraportti"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Tiestötarkastukset"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Kelitarkastusraportti"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Ilmoitukset"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Toimenpiteiden ajoittuminen"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Turvallisuusraportti"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Työmaakokousraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.tyomaakokous/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :toimenpidepaivat
    :parametrit   [{:tyyppi "hoitoluokat", :konteksti nil, :pakollinen true, :nimi "Hoitoluokat"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Toimenpidepäivät"
    :suorita      #'harja.palvelin.raportointi.raportit.toimenpidepaivat/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi            :suolasakko
    :parametrit      [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti       #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus          "Suolasakkoraportti"
    :kuvaus-tarkenne "Suolasakkoraportti (au)"
    :suorita         #'harja.palvelin.raportointi.raportit.suolasakko/suorita
    :urakkatyyppi    #{:hoito}}

   {:nimi             :indeksitarkistus
    :parametrit       [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti        #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus           "Indeksitarkistusraportti"
    :kuvaus-tarkenne  "Indeksitarkistusraportti (au jv)"
    :suorita          #'harja.palvelin.raportointi.raportit.indeksitarkistus/suorita
    :urakkatyyppi     #{:hoito :teiden-hoito}}

   {:nimi         :tiemerkinnan-kustannusyhteenveto
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Kustannusyhteenveto"
    :suorita      #'harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto/suorita
    :urakkatyyppi #{:tiemerkinta}}

   {:nimi         :ilmoitusraportti
    :parametrit   [{:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "urakoittain", :konteksti "elinvoimakeskus", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Ilmoitusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.ilmoitus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito :paallystys :tiemerkinta :valaistus :kaikki}}

   {:nimi         :siltatarkastus
    :parametrit   [{:tyyppi "urakan-vuosi", :konteksti nil, :pakollinen true, :nimi "Vuosi"}
                   {:tyyppi "silta", :konteksti "urakka", :pakollinen true, :nimi "Silta"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Siltatarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.siltatarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :muutos-ja-lisatyot
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka"} ;; Mahdollista näyttää vain urakkakohtaisesti, koska eri urakan alkuvuosina on erilaisen raportti
    :kuvaus       "Muutos- ja lisätyöt"
    :suorita      #'harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti/suorita
    :urakkatyyppi #{:teiden-hoito}}

   {:nimi         :toimenpidekilometrit
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "hoitoluokat", :konteksti nil, :pakollinen true, :nimi "Hoitoluokat"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Toimenpidekilometrit"
    :suorita      #'harja.palvelin.raportointi.raportit.toimenpidekilometrit/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :toimenpideajat
    :parametrit   [{:tyyppi "urakoittain", :konteksti "elinvoimakeskus", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "hoitoluokat", :konteksti nil, :pakollinen true, :nimi "Hoitoluokat"}
                   {:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Toimenpiteiden ajoittuminen"
    :suorita      #'harja.palvelin.raportointi.raportit.toimenpideajat/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi            :erilliskustannukset
    :parametrit      [{:tyyppi "urakan-toimenpide", :konteksti nil, :pakollinen false, :nimi "Toimenpide"}
                     {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti       #{"elinvoimakeskus" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus          "Erilliskustannukset"
    :kuvaus-tarkenne "Erilliskustannukset (au jv)"
    :suorita         #'harja.palvelin.raportointi.raportit.erilliskustannukset/suorita
    :urakkatyyppi    #{:hoito :teiden-hoito}}

   {:nimi         :valitavoiteraportti
    :parametrit   [{:tyyppi nil, :konteksti nil, :pakollinen nil, :nimi nil}]
    :konteksti    #{"urakka"}
    :kuvaus       "Välitavoiteraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.valitavoiteraportti/suorita
    :urakkatyyppi (set/union #{:hoito :teiden-hoito} urakka-domain/vesivayla-urakkatyypit)}

   {:nimi            :yks-hint-tyot
    :parametrit      [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                      {:tyyppi "urakan-toimenpide", :konteksti nil, :pakollinen false, :nimi "Toimenpide"}]
    :konteksti       #{"urakka"}
    :kuvaus          "Yksikköhintaiset työt päivittäin"
    :kuvaus-tarkenne "Yksikköhintaiset työt päivittäin (au jv)"
    :suorita         #'harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain/suorita
    :urakkatyyppi    #{:hoito}}

   {:nimi         :yllapidon-aikataulu
    :parametrit   [{:tyyppi        :valinta
                    :valinnat      [:aika :kohdenumero :tr]
                    :valinta-nayta {:aika        "Aloitusajan mukaan"
                                    :kohdenumero "Kohdenumeron mukaan"
                                    :tr          "Tieosoitteen mukaan"}
                    :nimi          :jarjestys
                    :otsikko       "Järjestä kohteet"}
                   {:tyyppi "urakan-vuosi", :konteksti "urakka", :pakollinen false, :nimi :vuosi}]
    :konteksti    #{"urakka"}
    :suorita      #'harja.palvelin.raportointi.raportit.yllapidon-aikataulu/suorita
    :kuvaus       "Ylläpidon aikataulu"
    :urakkatyyppi #{:paallystys :tiemerkinta}}

   {:nimi         :vastaanottotarkastusraportti
    :parametrit   [{:tyyppi "urakan-vuosi", :konteksti nil, :pakollinen true, :nimi "Vuosi"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka"}
    :suorita      #'harja.palvelin.raportointi.raportit.vastaanottotarkastus/suorita
    :kuvaus       "Vastaanottotarkastusraportti"
    :kuvaus-suuri-konteksti "Päällystysurakoiden yhteenveto"
    :urakkatyyppi #{:paallystys}}

   {:nimi         :vesivaylien-laskutusyhteenveto
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka" "elinvoimakeskus"}
    :kuvaus       "Laskutusyhteenveto"
    :suorita      #'harja.palvelin.raportointi.raportit.vesivaylien-laskutusyhteenveto/suorita
    :urakkatyyppi urakka-domain/vesivayla-urakkatyypit-ilman-kanavia}

   {:nimi         :kanavien-laskutusyhteenveto
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Laskutusyhteenveto"
    :suorita      #'harja.palvelin.raportointi.raportit.kanavien-laskutusyhteenveto/suorita
    :urakkatyyppi urakka-domain/kanava-urakkatyypit}

   {:nimi         :kanavien-muutos-ja-lisatyot
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "urakan-tehtava", :konteksti "urakka", :pakollinen false, :nimi "Tehtävä"}
                   {:tyyppi "kanavaurakan-kohde", :konteksti "urakka", :pakollinen false, :nimi "Kohde"}]
    :konteksti    #{"koko maa" "urakka"}
    :kuvaus       "Muutos- ja lisätyöt"
    :suorita      #'harja.palvelin.raportointi.raportit.kanavien-muutos-ja-lisatyot/suorita
    :urakkatyyppi urakka-domain/kanava-urakkatyypit}

   {:nimi         :kanavien-liikennetapahtumat
    :konteksti    #{}
    ;; Älä nosta PDF rajaa tälle raportille, aiheutuu liiallista muisti allocaatiota
    :rajoita-pdf-rivimaara  1200
    :kuvaus       "Liikennetapahtumat"
    :suorita      #'harja.palvelin.raportointi.raportit.kanavien-liikennetapahtumat/suorita
    :urakkatyyppi urakka-domain/kanava-urakkatyypit}

   {:nimi         :kanavien-hairiotilanteet
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Häiriötilanteet"
    :suorita      #'harja.palvelin.raportointi.raportit.kanavien-hairiotilanteet/suorita}

   {:nimi         :kanavien-kokonaishintaiset-toimenpiteet
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Kokonaishintaiset toimenpiteet"
    :suorita      #'harja.palvelin.raportointi.raportit.kanavien-toimenpiteet/suorita}

   {:nimi             :pohjavesialueiden-suolatoteumat
    :parametrit       [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti        #{"urakka"}
    :kuvaus           "Suolatoteumat - Kaikki pohjavesialueet"
    :kuvaus-tarkenne  "Suolatoteumat - Kaikki pohjavesialueet (jv)"
    :suorita          #'harja.palvelin.raportointi.raportit.pohjavesialueiden-suolat/suorita
    :urakkatyyppi     #{:hoito :teiden-hoito}}

   {:nimi         :suolatoteumat-rajoitusalueilla
    :parametrit   [{:tyyppi "aikavali", :konteksti "urakka", :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Suolatoteumat - Urakkasopimuksen rajoitusalueet"
    :suorita      #'harja.palvelin.raportointi.raportit.rajoitusalueiden-suolat/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi :talvisuolanlämpötilaraportti
    :konteksti #{"urakka"},
    :kuvaus-tarkenne "Talvisuolan kokonaiskäyttö"
    :kuvaus "Talvisuolanlämpötilaraportti"
    :suorita #'harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :kulut-tehtavaryhmittain
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "checkbox", :konteksti "koko maa", :pakollinen true, :nimi "Hallintayksiköittäin eroteltuna?"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Kulut tehtäväryhmittäin"
    :suorita      #'harja.palvelin.raportointi.raportit.kulut-tehtavaryhmittain/suorita
    :urakkatyyppi #{:teiden-hoito}}

   {:nimi         :paikkausten-yhteenveto
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka"}
    :suorita      #'harja.palvelin.raportointi.raportit.paikkausten-yhteenveto-mhu/suorita
    :kuvaus-tarkenne "Paikkausten yhteenveto MHU"
    :kuvaus       "MHUPaikkaustenyhteenveto"
    :urakkatyyppi #{:teiden-hoito}}

   {:nimi         :ppu-paikkausten-yhteenveto
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka"}
    :suorita      #'harja.palvelin.raportointi.raportit.paikkausten-yhteenveto/suorita-ppu
    :kuvaus-tarkenne "Paikkausten yhteenveto PPU"
    :kuvaus       "KokonaisurakanPaikkaustenyhteenveto"
    :urakkatyyppi #{:paallystys}
    :sopimustyyppi #{:kokonaisurakka}}

   {:nimi         :mpu-paikkausten-yhteenveto
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"elinvoimakeskus" "koko maa" "urakka"}
    :suorita      #'harja.palvelin.raportointi.raportit.paikkausten-yhteenveto/suorita-mpu
    :kuvaus-tarkenne "Paikkausten yhteenveto MPU"
    :kuvaus       "MPUPaikkaustenyhteenveto"
    :urakkatyyppi #{:paallystys}
    :sopimustyyppi #{:mpu}}

   {:nimi         :tiemerkinta-sakot-bonukset
    :konteksti    #{"urakka"}
    :urakkatyyppi #{:tiemerkinta}
    :kuvaus       "Tiemerkintä - Sakot ja bonukset"
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :suorita      #'harja.palvelin.raportointi.raportit.tiemerkinta-kustannukset/sakot-ja-bonukset}

   {:nimi         :tiemerkinta-muut-kustannukset
    :konteksti    #{"urakka"}
    :urakkatyyppi #{:tiemerkinta}
    :kuvaus       "Tiemerkintä - Muut kustannukset"
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :suorita      #'harja.palvelin.raportointi.raportit.tiemerkinta-kustannukset/muut-kustannukset}

   {:nimi :tiemerkinta-kustannukset-yhteenveto
    :konteksti    #{"urakka"}
    :urakkatyyppi #{:tiemerkinta}
    :kuvaus       "Tiemerkintä - Yhteenveto"
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :suorita      #'harja.palvelin.raportointi.raportit.tiemerkinta-kustannukset/yhteenveto}])

(def raportit-nimen-mukaan
  (into {} (map (juxt :nimi identity)) raportit))
