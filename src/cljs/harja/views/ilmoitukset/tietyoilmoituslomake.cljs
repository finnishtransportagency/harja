(ns harja.views.ilmoitukset.tietyoilmoituslomake
  (:require [reagent.core :as r]
            [harja.ui.lomake :as lomake]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [harja.domain.tietyoilmoitukset :as t]
            [reagent.core :refer [atom] :as r]
            [harja.ui.grid :refer [muokkaus-grid]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+
                                                  kuvaus-ja-avainarvopareja]]
            [harja.fmt :as fmt]
            [clojure.string :as str]
            [harja.ui.kentat :as kentat]))

(def koskee-valinnat [[nil "Ilmoitus koskee..."]
                      [:ensimmainen "Ensimmäinen ilmoitus työstä"],
                      [:muutos "Korjaus/muutos aiempaan tietoon"],
                      [:tyovaihe "Työvaihetta koskeva ilmoitus"],
                      [:paattyminen "Työn päättymisilmoitus"]]) ;; pidetäänkö päättymisilmoitus, oliko puhetta että jätetään pois?

(defn- urakka-valinnat [urakat]
  (into [[nil "Ei liity HARJA-urakkaan"]]
        (map (juxt :id :nimi))
        urakat))


(defn- pvm-vali-paivina [p1 p2]
  (when (and p1 p2)
    (.toFixed (/ (Math/abs (- p1 p2)) (* 1000 60 60 24)) 2)))

(def tyhja-kentta {:nimi :blank
                   :otsikko "" :hae (constantly "")
                   :muokattava false
                   :tyyppi :tyhja})

(defn dp [val msg]
  (log msg (with-out-str (cljs.pprint/pprint val)))
  val)

(defn- muunna-nopeusrajoitukset-muokkaus-gridille [nr-tiedot]
  ;; muuttaa ::t/nopeusrajoitukset -avaimen tiedont, esim:
  ;; [{:harja.domain.tietyoilmoitukset/rajoitus "30", :harja.domain.tietyoilmoitukset/matka 100}])
  ;; mapiksi {indeksi {rajoitus matka}} -tupleja, esim:
  ;; {0 {:rajoitus 100, :matka 200}, 1 {:rajoitus 300, :matka 20}}
  (apply merge
         (map-indexed
          (fn [indeksi rajoitus-map]
            {indeksi {:rajoitus (::t/rajoitus rajoitus-map) :matka (::t/matka rajoitus-map)}})
          nr-tiedot)))

(defn nopeusrajoitukset-komponentti-grid [e! nr-tiedot]
  (log "gridin dataksi r/wrapatty" (pr-str (muunna-nopeusrajoitukset-muokkaus-gridille nr-tiedot)))
  (log "nr-tiedot oli" (pr-str nr-tiedot))
  (if (some? nr-tiedot)
    [muokkaus-grid {:otsikko ""
                    :voi-muokata? (constantly true)
                    :voi-poistaa? (constantly true)
                    :piilota-toiminnot? false
                    :tyhja "Ei nopeusrajoituksia"
                    :jarjesta :jarjestysnro
                    :tunniste :jarjestysnro}
     [{:otsikko "Rajoitus (km/h)" :nimi :rajoitus :tyyppi :positiivinen-numero}
      {:otsikko "Matka (m)" :nimi :matka :tyyppi :positiivinen-numero }]
     (r/wrap (muunna-nopeusrajoitukset-muokkaus-gridille nr-tiedot)
             #(e! (tiedot/->PaivitaNopeusrajoituksetGrid %)))]
    ;; else
    (log "nr-komponentti sai nil")))

(def paiva-lyhyt #(str/upper-case (subs % 0 2)))

(defn tyoajat-komponentti-grid [e! tyoajat]
  [muokkaus-grid {:otsikko ""
                  :voi-muokata? (constantly true)
                  :voi-poistaa? (constantly true)
                  :piilota-toiminnot? false
                  :tyhja "Ei työaikoja"}
   [{:otsikko "Viikonpäivät" :nimi ::t/paivat :tyyppi :checkbox-group
     :tasaa :keskita
     :vaihtoehdot ["maanantai" "tiistai" "keskiviikko" "torstai" "perjantai" "lauantai" "sunnuntai"]
     :vaihtoehto-nayta paiva-lyhyt
     :nayta-rivina? true
     :leveys 5}
    {:otsikko "Alkuaika" :tyyppi :aika :placeholder "esim. 08:00" :nimi ::t/alkuaika
     :leveys 1}
    {:otsikko "Loppuaika" :tyyppi :aika :placeholder "esim. 18:00" :nimi ::t/loppuaika
     :leveys 1}
    ]
   (r/wrap (into {}
                 (map-indexed (fn [i ta]
                                [i (update ta ::t/paivat
                                           #(into #{} %))]))
                 tyoajat)
           #(e! (tiedot/->PaivitaTyoajatGrid (vals %))))])

(defn- valittu-tyon-tyyppi? [tyotyypit tyyppi]
  (some #(= (::t/tyyppi %) tyyppi) tyotyypit))

(defn- valitse-tyon-tyyppi [tyotyypit tyyppi valittu?]
  (if-not valittu?
    (vec (remove #(= (::t/tyyppi %) tyyppi) tyotyypit))
    (conj (or tyotyypit [])
          {::t/tyyppi tyyppi})))

(defn- tyotyypin-kuvaus [tyotyypit tyyppi]
  (some #(when (= (::t/tyyppi %) tyyppi)
           (::t/kuvaus %)) tyotyypit))

(defn- aseta-tyotyypin-kuvaus [tyotyypit tyyppi kuvaus]
  (mapv (fn [tt]
          (if (= (::t/tyyppi tt) tyyppi)
            (assoc tt ::t/kuvaus kuvaus)
            tt))
        tyotyypit))

(defn- tyotyypit []
  (let [osio (fn [nimi otsikko vaihtoehdot]
               {:otsikko "Tienrakennustyöt"
                :nimi nimi
                :hae ::t/tyotyypit
                :aseta #(assoc %1 ::t/tyotyypit %2)
                :tyyppi :checkbox-group
                :vaihtoehdot (map first vaihtoehdot)
                :vaihtoehto-nayta tiedot/tyotyyppi-vaihtoehdot-map
                :disabloi? (constantly false)
                :valittu-fn valittu-tyon-tyyppi?
                :valitse-fn valitse-tyon-tyyppi})]
    (lomake/ryhma
     "Työn tyyppi"
     (osio :tyotyypit-a "Tienrakennustyöt" tiedot/tyotyyppi-vaihtoehdot-tienrakennus)
     (osio :tyotyypit-b "Huolto- ja ylläpitotyöt" tiedot/tyotyyppi-vaihtoehdot-huolto)
     (osio :tyotyypit-c "Asennustyöt" tiedot/tyotyyppi-vaihtoehdot-asennus)
     (merge (osio :tyotyypit-d "Muut"  tiedot/tyotyyppi-vaihtoehdot-muut)
            {:muu-vaihtoehto "Muu, mikä?"
             :muu-kentta {:otsikko "" :nimi :muu-tyotyyppi-kuvaus :tyyppi :string
                          :hae #(tyotyypin-kuvaus % "Muu, mikä?")
                          :aseta #(aseta-tyotyypin-kuvaus %1 "Muu, mikä?" %2)
                          :placeholder "(Muu tyyppi?)"}}))))

(defn yhteyshenkilo [otsikko avain & kentat-ennen]
  (apply
   lomake/ryhma
   otsikko

   (concat kentat-ennen
           [{:nimi (keyword (name avain) "-etunimi")
             :otsikko "Yhteyshenkilön etunimi"
             :uusi-rivi? true
             :hae #(-> % avain ::t/etunimi)
             :aseta #(assoc-in %1 [avain ::t/etunimi] %2)
             :muokattava? (constantly true)
             :tyyppi :string}
            {:nimi (keyword (name avain) "-sukunimi")
             :otsikko "Yhteyshenkilön sukunimi"
             :hae #(-> % avain ::t/sukunimi)
             :aseta #(assoc-in %1 [avain ::t/sukunimi] %2)
             :muokattava? (constantly true)
             :tyyppi :string}
            {:nimi (keyword (name avain) "-matkapuhelin")
             :otsikko "Yhteyshenkilön puhelinnumero"
             :hae #(-> % avain ::t/matkapuhelin)
             :aseta #(assoc-in %1 [avain ::t/matkapuhelin] %2)
             :tyyppi :puhelin}
            {:nimi (keyword (name avain) "-sahkoposti")
             :otsikko "Yhteyshenkilön sähköposti"
             :hae #(-> % avain ::t/sahkoposti)
             :aseta #(assoc-in %1 [avain ::t/sahkoposti] %2)
             :tyyppi :string}])))

(defn lomake [e! tallennus-kaynnissa? ilmoitus kayttajan-urakat]
  [:div
   [:span
    [napit/takaisin "Palaa ilmoitusluetteloon" #(e! (tiedot/->PoistaIlmoitusValinta))]
    [lomake/lomake {:otsikko "Muokkaa ilmoitusta"
                    :muokkaa! #(do

                                 #_(log "muokkaa" (pr-str %))
                                 (e! (tiedot/->IlmoitustaMuokattu %)))}
     [(lomake/ryhma
       "Ilmoitus koskee"
       {:nimi :koskee
        :otsikko ""
        :tyyppi :valinta
        :valinnat koskee-valinnat
        :valinta-nayta second
        :valinta-arvo first
        :muokattava? (constantly true)}
       )
      (lomake/ryhma
       "Urakka"
       {:nimi ::t/urakka-id
        :otsikko "Liittyy urakkaan"
        :tyyppi :valinta
        :valinnat (urakka-valinnat kayttajan-urakat)
        :valinta-nayta second
        :valinta-arvo first
        :muokattava? (constantly true)}
       (if (:urakan-nimi-valinta ilmoitus)
         (assoc tyhja-kentta :nimi :blank-1)
         ;; else
         {:nimi :urakan-nimi-syotetty
          :uusi-rivi? true
          :otsikko "Projektin tai urakan nimi"
          :tyyppi :string
          :muokattava? (constantly true)})
       (if (:urakan-nimi-valinta ilmoitus)
         (assoc tyhja-kentta :nimi :blank-2)
         ;; else
         {:otsikko "Kohde urakassa"
          :nimi :kohde
          :tyyppi :string}))

      (yhteyshenkilo "Urakoitsijan yhteyshenkilo" ::t/urakoitsijayhteyshenkilo
                     {:nimi ::t/urakoitsijan-nimi
                      :otsikko "Nimi"
                      :muokattava? (constantly true)
                      :tyyppi :string})

      (yhteyshenkilo "Tilaaja" ::t/tilaajayhteyshenkilo
                     {:nimi ::t/tilaajan-nimi
                      :otsikko "Tilaajan nimi"
                      :muokattava? (constantly true)
                      :tyyppi :string})

      {:otsikko "Osoite"
       :nimi ::t/osoite
       :pakollinen? true
       :tyyppi :tierekisteriosoite
       :avaimet kentat/tr-osoite-domain-avaimet
       :ala-nayta-virhetta-komponentissa? true
       :validoi [[:validi-tr "Reittiä ei saada tehtyä" [::t/osoite]]]
       :sijainti (r/wrap (:sijainti ilmoitus) #(e! (tiedot/->PaivitaSijainti %)))
       }
      {:otsikko "Tien nimi" :nimi ::t/tien-nimi
       :tyyppi :string}
      {:otsikko "Kunta/kunnat" :nimi ::t/kunnat
       :tyyppi :string}
      {:otsikko "Työn alkupiste (osoite, paikannimi)" :nimi ::t/alkusijainnin-kuvaus
       :tyyppi :string}
      {:otsikko "Työn aloituspvm" :nimi ::t/alku :tyyppi :pvm}
      {:otsikko "Työn loppupiste (osoite, paikannimi)" :nimi ::t/loppusijainnin-kuvaus
       :tyyppi :string}
      {:otsikko "Työn lopetuspvm" :nimi ::t/loppu :tyyppi :pvm}
      {:otsikko "Työn pituus" :nimi :tyon-pituus
       :tyyppi :positiivinen-numero
       :placeholder "(Tyon pituus metreinä)"
       }

      #_(lomake/ryhma "Työvaihe"

                      {:otsikko "Työn alkupiste (osoite, paikannimi)" :nimi :alkusijainnin_kuvaus_b
                       :tyyppi :string}
                      {:otsikko "Työn aloituspvm" :nimi :alku_b :tyyppi :pvm}
                      {:otsikko "Työn loppupiste (osoite, paikannimi)" :nimi :loppusijainnin_kuvaus_b
                       :tyyppi :string}
                      {:otsikko "Työn lopetuspvm" :nimi :loppu_b :tyyppi :pvm}
                      {:otsikko "Työn pituus" :nimi :tyon-pituus_b
                       :tyyppi :positiivinen-numero
                       :hae #(pvm-vali-paivina (:alku %) (:loppu %))}
                      )
      (tyotyypit)
      {:otsikko "Päivittäinen työaika"
       :nimi ::t/tyoajat
       :tyyppi :komponentti
       :komponentti #(->> % :data ::t/tyoajat (tyoajat-komponentti-grid e!))
       :palstoja 2}
      (lomake/ryhma "Vaikutukset liikenteelle"
                    {:otsikko "Arvioitu viivytys normaalissa liikenteessä (min)"
                     :nimi ::t/viivastys-normaali-liikenteessa
                     :tyyppi :positiivinen-numero}
                    {:otsikko "Arvioitu viivytys ruuhka-aikana (min)"
                     :nimi ::t/viivastys-ruuhka-aikana
                     :tyyppi :positiivinen-numero
                     }
                    {:otsikko "Kaistajärjestelyt"
                     :tyyppi :checkbox-group
                     :nimi :tietyon_kaistajarjestelyt
                     :vaihtoehdot (map first tiedot/kaistajarjestelyt-vaihtoehdot-map)
                     :vaihtoehto-nayta tiedot/kaistajarjestelyt-vaihtoehdot-map
                     :muu-vaihtoehto "Muu"
                     :muu-kentta {:otsikko "" :nimi :jotain :tyyppi :string :placeholder "(muu kaistajärjestely?)"}}
                    {:otsikko "Nopeusrajoitukset"
                     :tyyppi :komponentti
                     :komponentti #(->> % :data ::t/nopeusrajoitukset (nopeusrajoitukset-komponentti-grid e!))
                     :nimi :nopeusrajoitukset
                     }
                    {:otsikko "Kokorajoituksia"
                     :tyyppi :checkbox-group
                     :nimi :kokorajoituksia
                     :vaihtoehdot ["Ulottumarajoituksia" ;; ->max leveys, korkeus
                                   "Painorajoitus"] ;; -> max paino

                     }
                    {:otsikko "Tien pinta työmaalla"
                     ;; tehdään muokkaus-grid tähän?
                     :tyyppi :checkbox-group
                     :nimi :tienpinta-materiaali
                     :vaihtoehdot ["Päällystetty", "Jyrsitty", "Murske"]

                     }
                    {:otsikko "Metriä:"
                     :tyyppi :positiivinen-numero
                     :nimi :tienpinta-matka}
                    {:otsikko "Kiertotie"
                     :tyyppi :checkbox-group
                     :nimi :kiertotien-pinnat-ja-mutkaisuus ;; scheman mutkaisuus + pinnat combo

                     :valinnat ["Loivat mutkat", "Jyrkät mutkat (erkanee yli 45° kulmassa)" "Päällystetty" "Murske" "Kantavuus rajoittaa", "___ tonnia"]
                     ;; ___ metriä
                     ;; __ tonnia

                     }
                    {:otsikko "Kulkurajoituksia"
                     :tyyppi :checkbox-group
                     :nimi :liikenteenohjaus
                     :vaihtoehdot ["Liikennevalot" "Liikenteen ohjaaja" "Satunnaisia (aikataulu, jos yli 5 min)" "Aikataulu:"]
                     :vaihtoehto-nayta identity
                     :muu-vaihtoehto "Aikataulu:"
                     :muu-kentta {:otsikko "" :nimi :jotain :tyyppi :string :placeholder "(muu kaistajärjestely?)"}
                     }
                    )
      (lomake/ryhma "Vaikutussuunta"
                    {:otsikko ""
                     :tyyppi :checkbox-group
                     :nimi ::t/vaikutussuunta
                     :vaihtoehdot (map first tiedot/vaikutussuunta-vaihtoehdot-map) ;; -> kaupunki?
                     :vaihtoehto-nayta tiedot/vaikutussuunta-vaihtoehdot-map
                     ;; muu?
                     }

                    )
      (lomake/ryhma "Lisätietoja"
                    {:otsikko ""
                     :nimi ::t/lisatietoja
                     :tyyppi :string
                     })
      (yhteyshenkilo "Ilmoittaja" ::t/ilmoittaja)

      ]
     ilmoitus]]
   [napit/tallenna
    "Tallenna ilmoitus"
    #(e! (tiedot/->TallennaIlmoitus (lomake/ilman-lomaketietoja ilmoitus)))
    {:disabled tallennus-kaynnissa?
     :tallennus-kaynnissa? tallennus-kaynnissa?
     :ikoni (ikonit/tallenna)}]])
