(ns harja.palvelin.palvelut.kulut.paatos-apurit
  "Välikatselmusten päätöksien yhteydessä hallitaan automaattisesti lupausbonuksia, lupaussanktioita ja kuluja.
  Näiden erillisten laajennusten/riippuvuuksien hallinta on eriytetty tähän erilliseen tiedostoon, jotta
  koodin luettavuus ja testattavuus paranee."
  (:require
    [slingshot.slingshot :refer [throw+]]
    [taoensso.timbre :as log]
    [harja.pvm :as pvm]
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.urakka :as urakka]
    [harja.domain.kulut :as kulut-domain]
    [harja.domain.lupaus-domain :as lupaus-domain]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.valikatselmus :as valikatselmus-q]
    [harja.kyselyt.urakat :as urakat-q]
    [harja.kyselyt.konversio :as konv]
    [harja.kyselyt.kulut :as kulut-q]
    [harja.kyselyt.toteumat :as toteumat-q]
    [harja.kyselyt.sanktiot :as sanktiot-q]
    [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
    [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]))

(defn- heita-virhe [viesti] (throw+ {:type "Error"
                                    :virheet {:koodi "ERROR" :viesti viesti}}))

(defn lupauksen-indeksi
  "Lupausbonukselle ja -sanktiolle lisätään indeksi urakan tiedoista, mikäli ne on MH-urakoita ja alkavat vuonna -19 tai -20"
  [urakan-tiedot]
  (if (and
        (= "teiden-hoito" (:tyyppi urakan-tiedot))
        (or
          (= (-> urakan-tiedot :alkupvm pvm/vuosi) 2020)
          (= (-> urakan-tiedot :alkupvm pvm/vuosi) 2019)))
    (:indeksi urakan-tiedot)
    nil))

(defn tallenna-lupaussanktio [db paatoksen-tiedot kayttaja]
  (when (= ::valikatselmus/lupaussanktio (::valikatselmus/tyyppi paatoksen-tiedot))
    (let [urakka-id (::urakka/id paatoksen-tiedot)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          toimenpideinstanssi-id (valikatselmus-q/hae-urakan-bonuksen-toimenpideinstanssi-id db urakka-id)
          perustelu (str "Urakoitsija sai " (::valikatselmus/lupaus-toteutuneet-pisteet paatoksen-tiedot)
                      " pistettä ja lupasi " (::valikatselmus/lupaus-luvatut-pisteet paatoksen-tiedot) " pistettä.")
          kohdistuspvm (konv/sql-date (pvm/luo-pvm-dec-kk (inc (::valikatselmus/hoitokauden-alkuvuosi paatoksen-tiedot)) 9 15))

          ; "Riippuen urakan alkuvuodesta, indeksejä ei välttämättä käytetä sakoissa/sanktioissa. MHU urakoissa joiden alkuvuosi 2021 tai eteenpäin niitä ei sidota indeksiin"
          indeksi (lupauksen-indeksi urakka)
          laatupoikkeama {:tekijanimi (:nimi kayttaja)
                          :paatos {:paatos "sanktio", :kasittelyaika (pvm/nyt), :perustelu perustelu, :kasittelytapa :valikatselmus}
                          :kohde nil
                          :aika kohdistuspvm
                          :urakka urakka-id
                          :yllapitokohde nil}
          lupaussanktiotyyppi (first (sanktiot-q/hae-sanktiotyypin-tiedot-koodilla db {:koodit 0}))
          sanktio {:kasittelyaika (pvm/nyt)
                   :suorasanktio true,
                   :laji :lupaussanktio,
                   :summa (::valikatselmus/urakoitsijan-maksu paatoksen-tiedot),
                   :toimenpideinstanssi toimenpideinstanssi-id,
                   :perintapvm kohdistuspvm
                   ;; Lupaussanktion tyyppiä ei tarvitse valita
                   :tyyppi lupaussanktiotyyppi
                   :indeksi indeksi}

          ;; Tallennus palauttaa sanktio-id:n
          uusin-sanktio-id (laadunseuranta-palvelu/tallenna-suorasanktio db kayttaja sanktio laatupoikkeama urakka-id [nil nil])]
      uusin-sanktio-id)))

(defn tallenna-lupausbonus
  "Lupauspäätöstä tallennettaessa voidaan tallentaa myös lupausbonus"
  [db paatoksen-tiedot kayttaja]
  (when (= ::valikatselmus/lupausbonus (::valikatselmus/tyyppi paatoksen-tiedot))
    (let [urakka-id (::urakka/id paatoksen-tiedot)
          urakan-tiedot (first (urakat-q/hae-urakka db urakka-id))
          indeksin-nimi (lupauksen-indeksi urakan-tiedot)
          toimenpideinstanssi-id (valikatselmus-q/hae-urakan-bonuksen-toimenpideinstanssi-id db urakka-id)
          sopimus-id (:id (first (urakat-q/hae-urakan-sopimukset db urakka-id)))
          ;; Asetetaan päivämäärä hoitokauden viimeiselle kuukaudelle
          laskutuspvm (konv/sql-date (pvm/luo-pvm-dec-kk (inc (::valikatselmus/hoitokauden-alkuvuosi paatoksen-tiedot)) 9 15))
          lisatiedot (str "Urakoitsija sai " (::valikatselmus/lupaus-toteutuneet-pisteet paatoksen-tiedot)
                       " pistettä ja lupasi " (::valikatselmus/lupaus-luvatut-pisteet paatoksen-tiedot) " pistettä.")
          ek {:tyyppi "lupausbonus"
              :urakka urakka-id
              :sopimus sopimus-id
              :toimenpideinstanssi toimenpideinstanssi-id
              :pvm laskutuspvm
              :rahasumma (::valikatselmus/tilaajan-maksu paatoksen-tiedot)
              :indeksin_nimi indeksin-nimi
              :lisatieto lisatiedot
              :laskutuskuukausi laskutuspvm
              :kasittelytapa "valikatselmus"
              :luoja (:id kayttaja)}
          bonus (toteumat-q/luo-erilliskustannus<! db ek)]
      (:id bonus))))

(defn tallenna-kulu
  "Välikatselmuksen päätöksestä voi tulla automaattisia kuluja. Tavoitehinnan alituksesta muodostetaan kulu, jonka tilaaja maksaa.
  Tavoite- ja kattohinnan ylitykistä luodaan negatiivinen kulu, joka tulee urakoitsijan maksettavaksi."
  [db paatoksen-tiedot kayttaja paatoksen-tyyppi]
  (when (and
          ;; Vaadi kulun summa
          (::valikatselmus/urakoitsijan-maksu paatoksen-tiedot)
          ;; Varmista oikea päätöksen tyyppi
          (or
            (= ::valikatselmus/tavoitehinnan-ylitys paatoksen-tyyppi)
            (= ::valikatselmus/tavoitehinnan-alitus paatoksen-tyyppi)
            (= ::valikatselmus/kattohinnan-ylitys paatoksen-tyyppi)))
    (let [urakka-id (::urakka/id paatoksen-tiedot)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          tehtavaryhman-avain (cond
                                (= ::valikatselmus/tavoitehinnan-ylitys paatoksen-tyyppi)
                                "19907c24-dd26-460f-9cb4-2ed974b891aa"
                                (= ::valikatselmus/tavoitehinnan-alitus paatoksen-tyyppi)
                                "55c920e7-5656-4bb0-8437-1999add714a3" ; Tavoitepalkkio
                                (= ::valikatselmus/kattohinnan-ylitys paatoksen-tyyppi)
                                "be34116b-2264-43e0-8ac8-3762b27a9557"
                                :else nil)
          ;; Haetaan avaimen perusteella tehtäväryhmä
          tehtavaryhma (first (kulut-q/hae-tehtavaryhman-tiedot-tunnisteella db {:tunniste tehtavaryhman-avain}))
          toimenpideinstanssi-id (:id (first (kulut-q/hae-urakan-hoidon-johdon-toimenpideinstanssi db {:urakka urakka-id})))
          lisatiedot (cond
                       (= ::valikatselmus/tavoitehinnan-ylitys paatoksen-tyyppi)
                       "Välikatselmuksessa luotu kulu. Tavoitehinta ylitettiin. Urakoitsijalle kulu."
                       (= ::valikatselmus/tavoitehinnan-alitus paatoksen-tyyppi)
                       "Välikatselmuksessa luotu kulu. Tavoitehinta alitettiin. Tilaajalle kulu."
                       (= ::valikatselmus/kattohinnan-ylitys paatoksen-tyyppi)
                       "Välikatselmuksessa luotu kulu. Tavoitehinta ja kattohinta ylitettiin. Urakoitsijalle kulu."
                       :else nil)
          ;; Asetetaan päivämäärä hoitokauden viimeiselle kuukaudelle
          laskutuspvm (konv/sql-date (pvm/luo-pvm-dec-kk (inc (::valikatselmus/hoitokauden-alkuvuosi paatoksen-tiedot)) 9 15))
          kokonaissumma (if (= ::valikatselmus/tavoitehinnan-alitus paatoksen-tyyppi)
                          (* -1 (::valikatselmus/urakoitsijan-maksu paatoksen-tiedot))
                          (::valikatselmus/urakoitsijan-maksu paatoksen-tiedot))
          ;; Kulu
          kulu {:tyyppi "laskutettava"
                :numero nil
                :koontilaskun-kuukausi (kulut-domain/pvm->koontilaskun-kuukausi laskutuspvm (:alkupvm urakka))
                ;; Summa on negatiivinen, mikäli urakoitsija joutuu maksumieheksi
                :kokonaissumma (if (or (= ::valikatselmus/tavoitehinnan-ylitys paatoksen-tyyppi)
                                     (= ::valikatselmus/kattohinnan-ylitys paatoksen-tyyppi))
                                 (* -1 kokonaissumma)
                                 kokonaissumma)
                :erapaiva laskutuspvm
                :urakka urakka-id
                :kayttaja (:id kayttaja)
                :lisatieto lisatiedot}
          uusin-kulu (kulut-q/luo-kulu<! db kulu)
          uusi-kulu-id (:id uusin-kulu)

          ;; Tallenna kulu_kohdistus
          kulukohdistus {:id nil
                         :rivi 0
                         :kulu uusi-kulu-id
                         :summa (:kokonaissumma kulu)
                         :toimenpideinstanssi toimenpideinstanssi-id
                         :tehtavaryhma (:id tehtavaryhma)
                         :maksueratyyppi "kokonaishintainen"
                         :alkupvm laskutuspvm
                         :loppupvm laskutuspvm
                         :kayttaja (:id kayttaja)
                         :lisatyon-lisatieto lisatiedot}
          _ (kulut-q/luo-kulun-kohdistus<! db kulukohdistus)]
      uusi-kulu-id)))

(defn tarkista-lupausbonus
  "Varmista, että annettu bonus täsmää lupauksista saatavaan bonukseen"
  [db kayttaja tiedot]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id (::urakka/id tiedot)}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        hakuparametrit {:urakka-id (::urakka/id tiedot)
                        :urakan-alkuvuosi urakan-alkuvuosi
                        :nykyhetki (pvm/nyt)
                        :valittu-hoitokausi [(pvm/luo-pvm (::valikatselmus/hoitokauden-alkuvuosi tiedot) 9 1)
                                             (pvm/luo-pvm (inc (::valikatselmus/hoitokauden-alkuvuosi tiedot)) 8 30)]}
        vanha-mhu? (lupaus-domain/urakka-19-20? urakan-tiedot)
        lupaustiedot (if vanha-mhu?
                       (lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle db kayttaja hakuparametrit)
                       (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db hakuparametrit))
        tilaajan-maksu (bigdec (::valikatselmus/tilaajan-maksu tiedot))
        laskettu-bonus (bigdec (or (get-in lupaustiedot [:yhteenveto :bonus-tai-sanktio :bonus]) 0M))]
    (cond (and
            ;; Varmistetaan, että tyyppi täsmää
            (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupausbonus)
            ;; Tarkistetaan, että bonus on annettu, jotta voidaan tarkistaa luvut
            (get-in lupaustiedot [:yhteenveto :bonus-tai-sanktio :bonus])
            ;; Varmistetaan, että lupauksissa laskettu bonus täsmää päätöksen bonukseen
            (= laskettu-bonus tilaajan-maksu))
      true
      (and
        ;; Varmistetaan, että tyyppi täsmää
        (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupausbonus)
        ;; Tarkistetaan, että tavoite on täytetty, eli nolla case, jotta voidaan tarkistaa luvut
        (get-in lupaustiedot [:yhteenveto :bonus-tai-sanktio :tavoite-taytetty])
        ;; Varmistetaan, että lupauksissa laskettu sanktio täsmää päätöksen sanktioon
        (= (bigdec 0) tilaajan-maksu))
      true
      :else
      (do
        (log/warn "Lupausbonuksen tilaajan maksun summa ei täsmää lupauksissa lasketun bonuksen kanssa.
            Laskettu bonus: " laskettu-bonus " tilaajan maksu: " tilaajan-maksu)
        (heita-virhe "Lupausbonuksen tilaajan maksun summa ei täsmää lupauksissa lasketun bonuksen kanssa.")))))

(defn tarkista-lupaussanktio
  "Varmista, että tuleva sanktio täsmää lupauksista saatavaan sanktioon"
  [db kayttaja tiedot]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id (::urakka/id tiedot)}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        hakuparametrit {:urakka-id (::urakka/id tiedot)
                        :urakan-alkuvuosi urakan-alkuvuosi
                        :nykyhetki (pvm/nyt)
                        :valittu-hoitokausi [(pvm/luo-pvm (::valikatselmus/hoitokauden-alkuvuosi tiedot) 9 1)
                                             (pvm/luo-pvm (inc (::valikatselmus/hoitokauden-alkuvuosi tiedot)) 8 30)]}
        ;; Lupauksia käsitellään täysin eri tavalla riippuen urakan alkuvuodesta
        lupaukset (if (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
                    (lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle db kayttaja hakuparametrit)
                    (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db hakuparametrit))]
    (cond (and
            ;; Varmistetaan, että tyyppi täsmää
            (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupaussanktio)
            ;; Tarkistetaan, että sanktio on annettu, jotta voidaan tarkistaa luvut
            (get-in lupaukset [:yhteenveto :bonus-tai-sanktio :sanktio])
            ;; Varmistetaan, että lupauksissa laskettu sanktio täsmää päätöksen sanktioon
            (= (bigdec (get-in lupaukset [:yhteenveto :bonus-tai-sanktio :sanktio])) (bigdec (::valikatselmus/urakoitsijan-maksu tiedot))))
      true
      (and
        ;; Varmistetaan, että tyyppi täsmää
        (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupaussanktio)
        ;; Tarkistetaan, että tavoite on täytetty, eli nolla case, jotta voidaan tarkistaa luvut
        (get-in lupaukset [:yhteenveto :bonus-tai-sanktio :tavoite-taytetty])
        ;; Varmistetaan, että lupauksissa laskettu sanktio täsmää päätöksen sanktioon
        (= (bigdec 0) (bigdec (::valikatselmus/urakoitsijan-maksu tiedot))))
      true
      :else
      (heita-virhe "Lupaussanktion urakoitsijan maksun summa ei täsmää lupauksissa lasketun sanktion kanssa."))))
