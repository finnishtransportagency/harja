(ns harja.palvelin.raportointi.raportit
  "Sisältää kaikki Harjan raportit. Nämä tiedot ennen ladattiin tietokannasta,
  nyt ne on määritelty kätevästi `raportit` vektorissa.

  Jos lisäät uuden raportin, lisää sen nimiavaruuden require alle sekä
  raportin tiedot `raportit` vektoriin."

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
  [harja.palvelin.raportointi.raportit.muutos-ja-lisatyot]
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
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Sakko- ja bonusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.sanktioraportti-yllapito/suorita
    :urakkatyyppi #{:paallystys :paikkaus :tiemerkinta}}

   {:nimi         :soratietarkastusraportti
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "tienumero", :konteksti nil, :pakollinen false, :nimi "Tienumero"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Soratietarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.soratietarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :laskutusyhteenveto
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "urakka"}
    :kuvaus       "Laskutusyhteenveto"
    :suorita      #'harja.palvelin.raportointi.raportit.laskutusyhteenveto/suorita
    :urakkatyyppi #{:hoito}}

   {:nimi         :laskutusyhteenveto-tuotekohtainen
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "urakka"}
    :kuvaus       "Laskutusyhteenveto"
    :kuvaus-tarkenne "Laskutusyhteenveto (tuotekohtainen)"
    :suorita      #'harja.palvelin.raportointi.raportit.laskutusyhteenveto-tuotekohtainen/suorita
    :urakkatyyppi #{:teiden-hoito}}

   {:nimi         :laskutusyhteenveto-tyomaa
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "urakka"}
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
    :konteksti #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus "Tehtävämäärät"
    :suorita #'harja.palvelin.raportointi.raportit.tehtavamaarat/suorita
    :urakkatyyppi #{:teiden-hoito}}

   {:nimi :vemtr
    :parametrit [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                 {:tyyppi "checkbox" :konteksti nil, :pakollinen false :nimi "Vain MHUt ja HJU:t"}]
    :konteksti #{"hallintayksikko" "koko maa"}
    :kuvaus "Valtakunnalliset ja ELY-kohtaiset määrätoteumat"
    :suorita #'harja.palvelin.raportointi.raportit.vemtr/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :laaduntarkastusraportti
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "tienumero", :konteksti nil, :pakollinen false, :nimi "Tienumero"}
                   {:tyyppi "checkbox", :konteksti nil, :pakollinen false, :nimi "Vain laadun alitukset"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Laaduntarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.laaduntarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :sanktioraportti
    :kuvaus-tarkenne "Sanktiot, bonukset ja arvonvähennykset"
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Sanktioiden yhteenveto"
    :suorita      #'harja.palvelin.raportointi.raportit.sanktio/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :kelitarkastusraportti
    :parametrit   [{:tyyppi "tienumero", :konteksti nil, :pakollinen false, :nimi "Tienumero"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Kelitarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.kelitarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :tiestotarkastusraportti
    :rajoita-pdf-rivimaara  30000
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "tienumero", :konteksti nil, :pakollinen false, :nimi "Tienumero"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Tiestötarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.tiestotarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :ymparistoraportti
    :parametrit   [{:tyyppi "urakoittain", :konteksti nil, :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "urakkanumero", :konteksti nil, :pakollinen true, :nimi "Näytä urakkanumerot"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Ympäristöraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.ymparisto/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :turvallisuus
    :parametrit   [{:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "hallintayksikko", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Turvallisuusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat/suorita
    :urakkatyyppi (set/union #{:hoito :teiden-hoito :paallystys :paikkaus :tiemerkinta} urakka-domain/vesivayla-urakkatyypit)}

   {:nimi         :yks-hint-kuukausiraportti
    :parametrit   [{:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "urakoittain", :konteksti "hallintayksikko", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakan-toimenpide", :konteksti nil, :pakollinen false, :nimi "Toimenpide"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Yksikköhintaiset työt kuukausittain"
    :suorita      #'harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain/suorita
    :urakkatyyppi #{:hoito}}

   {:nimi         :materiaaliraportti
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Materiaaliraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.materiaali/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :laatupoikkeamaraportti
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "laatupoikkeamatekija", :konteksti nil, :pakollinen true, :nimi "Laatupoikkeamatekija"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Laatupoikkeamaraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.laatupoikkeama/suorita
    :urakkatyyppi (set/union #{:hoito :teiden-hoito :paallystys :paikkaus :tiemerkinta} urakka-domain/vesivayla-urakkatyypit)}

   {:nimi         :yks-hint-tehtavien-summat
    :parametrit   [{:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakan-toimenpide", :konteksti nil, :pakollinen false, :nimi "Toimenpide"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "urakoittain", :konteksti "hallintayksikko", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Yksikköhintaiset työt tehtävittäin"
    :suorita      #'harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain/suorita
    :urakkatyyppi #{:hoito}}

   {:nimi         :tyomaakokous
    :parametrit   [{:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Sanktioiden yhteenveto"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Muutos- ja lisätyöt"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Laskutusyhteenveto"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Laatupoikkeamat"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Soratietarkastukset"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Laaduntarkastusraportti"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Tiestötarkastukset"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Kelitarkastusraportti"}
                   {:tyyppi "checkbox", :konteksti "urakka", :pakollinen true, :nimi "Ympäristöraportti"}
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
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Toimenpidepäivät"
    :suorita      #'harja.palvelin.raportointi.raportit.toimenpidepaivat/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :suolasakko
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Suolasakkoraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.suolasakko/suorita
    :urakkatyyppi #{:hoito}}

   {:nimi         :indeksitarkistus
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Indeksitarkistusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.indeksitarkistus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

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
                   {:tyyppi "urakoittain", :konteksti "hallintayksikko", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Ilmoitusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.ilmoitus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito :paallystys :tiemerkinta :valaistus :kaikki}}

   {:nimi         :siltatarkastus
    :parametrit   [{:tyyppi "urakan-vuosi", :konteksti nil, :pakollinen true, :nimi "Vuosi"}
                   {:tyyppi "silta", :konteksti "urakka", :pakollinen true, :nimi "Silta"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Siltatarkastusraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.siltatarkastus/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :muutos-ja-lisatyot
    :parametrit   [{:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakan-toimenpide", :konteksti nil, :pakollinen false, :nimi "Toimenpide"}
                   {:tyyppi "muutostyotyyppi", :konteksti nil, :pakollinen false, :nimi "Työn tyyppi"}
                   {:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "hallintayksikko", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Muutos- ja lisätyöt"
    :suorita      #'harja.palvelin.raportointi.raportit.muutos-ja-lisatyot/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :toimenpidekilometrit
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "hoitoluokat", :konteksti nil, :pakollinen true, :nimi "Hoitoluokat"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Toimenpidekilometrit"
    :suorita      #'harja.palvelin.raportointi.raportit.toimenpidekilometrit/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :toimenpideajat
    :parametrit   [{:tyyppi "urakoittain", :konteksti "hallintayksikko", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "hoitoluokat", :konteksti nil, :pakollinen true, :nimi "Hoitoluokat"}
                   {:tyyppi "urakoittain", :konteksti "koko maa", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "urakoittain", :konteksti "hankinta-alue", :pakollinen true, :nimi "Näytä urakka-alueet eriteltynä"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Toimenpiteiden ajoittuminen"
    :suorita      #'harja.palvelin.raportointi.raportit.toimenpideajat/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :erilliskustannukset
    :parametrit   [{:tyyppi "urakan-toimenpide", :konteksti nil, :pakollinen false, :nimi "Toimenpide"}
                   {:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"hallintayksikko" "koko maa" "urakka" "hankinta-alue"}
    :kuvaus       "Erilliskustannukset"
    :suorita      #'harja.palvelin.raportointi.raportit.erilliskustannukset/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :valitavoiteraportti
    :parametrit   [{:tyyppi nil, :konteksti nil, :pakollinen nil, :nimi nil}]
    :konteksti    #{"urakka"}
    :kuvaus       "Välitavoiteraportti"
    :suorita      #'harja.palvelin.raportointi.raportit.valitavoiteraportti/suorita
    :urakkatyyppi (set/union #{:hoito :teiden-hoito} urakka-domain/vesivayla-urakkatyypit)}

   {:nimi         :yks-hint-tyot
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "urakan-toimenpide", :konteksti nil, :pakollinen false, :nimi "Toimenpide"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Yksikköhintaiset työt päivittäin"
    :suorita      #'harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain/suorita
    :urakkatyyppi #{:hoito}}

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
    :konteksti    #{"hallintayksikko" "koko maa" "urakka"}
    :suorita      #'harja.palvelin.raportointi.raportit.vastaanottotarkastus/suorita
    :kuvaus       "Vastaanottotarkastusraportti"
    :kuvaus-suuri-konteksti "Päällystysurakoiden yhteenveto"
    :urakkatyyppi #{:paallystys}}

   {:nimi         :vesivaylien-laskutusyhteenveto
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka" "hallintayksikko"}
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

   {:nimi         :pohjavesialueiden-suolatoteumat
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Suolatoteumat - Kaikki pohjavesialueet"
    :suorita      #'harja.palvelin.raportointi.raportit.pohjavesialueiden-suolat/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :suolatoteumat-rajoitusalueilla
    :parametrit   [{:tyyppi "aikavali", :konteksti "urakka", :pakollinen true, :nimi "Aikaväli"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Suolatoteumat - Urakkasopimuksen rajoitusalueet"
    :suorita      #'harja.palvelin.raportointi.raportit.rajoitusalueiden-suolat/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi :talvisuolanlämpötilaraportti
    :konteksti #{"urakka"},
    :kuvaus-tarkenne "Talvisuolan lämpötilatarkastelu"
    :kuvaus "Talvisuolanlämpötilaraportti"
    :suorita #'harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara/suorita
    :urakkatyyppi #{:hoito :teiden-hoito}}

   {:nimi         :kulut-tehtavaryhmittain
    :parametrit   [{:tyyppi "aikavali", :konteksti nil, :pakollinen true, :nimi "Aikaväli"}
                   {:tyyppi "checkbox", :konteksti "koko maa", :pakollinen true, :nimi "Hallintayksiköittäin eroteltuna?"}]
    :konteksti    #{"urakka"}
    :kuvaus       "Kulut tehtäväryhmittäin"
    :suorita      #'harja.palvelin.raportointi.raportit.kulut-tehtavaryhmittain/suorita
    :urakkatyyppi #{:teiden-hoito}}])

(def raportit-nimen-mukaan
  (into {} (map (juxt :nimi identity)) raportit))

