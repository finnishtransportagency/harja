(ns harja.views.ilmoitukset.tietyoilmoituslomake
  (:require [reagent.core :as r]
            [harja.ui.lomake :as lomake]
            [harja.pvm :as pvm]
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
                                                  kuvaus-ja-avainarvopareja]]))

(def koskee-valinnat [[nil "Ilmoitus koskee..."]
                      [:ensimmainen "Ensimmäinen ilmoitus työstä"],
                      [:muutos "Korjaus/muutos aiempaan tietoon"],
                      [:tyovaihe "Työvaihetta koskeva ilmoitus"],
                      [:paattyminen "Työn päättymisilmoitus"]]) ;; pidetäänkö päättymisilmoitus, oliko puhetta että jätetään pois?

(defn- urakka-valinnat [urakat]
  (into [[nil "Ei liity HARJA-urakkaan"]]
        (map (juxt (comp str :id) :nimi))
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

(defn nopeusrajoitukset-komponentti [nopeusrajoitus-tiedot]
  (assert (map? nopeusrajoitus-tiedot))
  (log "nr-komponentti, tiedot" (pr-str nopeusrajoitus-tiedot))
  [:div
   "seppo"
   #_(for [[i rajoitus matka] (map-indexed #([%1 (:t/rajoitus %2) (:t/matka %2)]) nopeusrajoitus-tiedot)
         {::t/keys [rajoitus matka]} nopeusrajoitus-tiedot
         ]
     (do
       (log "rajoitus" rajoitus "matka" matka)
       ^{:key i }[:span rajoitus matka]))])

(defn lomake [e! ilmoitus kayttajan-urakat]
  (fn [e! ilmoitus]
    ;; (log "rendataan ilmoitusta, tien-nimi:" (pr-str (::t/tien-nimi ilmoitus)))


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
        (lomake/ryhma "Tiedot koko kohteesta"
                      {:nimi :urakan-nimi-valinta
                       :otsikko "Liittyy urakkaan"
                       :tyyppi :valinta
                       :valinnat (urakka-valinnat kayttajan-urakat)
                       :valinta-nayta second :valinta-arvo first
                       :muokattava? (constantly true)}
                      (if (:urakan-nimi-valinta ilmoitus)
                        (assoc tyhja-kentta :nimi :blank-1)
                        ;; else
                        {:nimi :urakan-nimi-syotetty
                         :otsikko "Projektin tai urakan nimi"
                         :tyyppi :string
                         :muokattava? (constantly true)})
                      (if (:urakan-nimi-valinta ilmoitus)
                        (assoc tyhja-kentta :nimi :blank-2)
                        ;; else
                        {:otsikko "Kohde urakassa"
                         :nimi :kohde
                         :tyyppi :string})

                      {:nimi ::t/urakoitsijan-nimi
                       :otsikko "Urakoitsijan nimi"
                       :muokattava? (constantly true)
                       :tyyppi :string}
                      {:nimi :urakoitisijan-yhteyshenkilo-nimi
                       :otsikko "Urakoitsijan yhteyshenkilö"
                       ;; tuleeko yhdistämisessä muoakttaessa haankluuksia kun pitää taas erottaa?
                       ;; -> ehkä täytyy poiketa rautalangasta tässä
                       :hae #(-> %  ::t/urakoitsijayhteyshenkilo tiedot/henkilo->nimi)
                       :muokattava? (constantly true)
                       :tyyppi :string}
                      {:nimi :urakoitisijanyhteyshenkilo-matkapuhelin
                       :otsikko "Puhelinnumero"
                       :hae #(-> % ::t/urakoitsijayhteyshenkilo ::t/matkapuhelin)
                       :tyyppi :puhelin}
                      {:nimi ::t/tilaajan-nimi
                       :otsikko "Tilaajan nimi"
                       :muokattava? (constantly true)
                       :tyyppi :string}
                      {:nimi :tilaajan-yhteyshenkilo-nimi
                       :otsikko "Tilaajan yhteyshenkilö"
                       :hae #(-> %  ::t/tilaajayhteyshenkilo tiedot/henkilo->nimi)

                       :muokattava? (constantly true)
                       :tyyppi :string}
                      {:nimi :tilaajan-yhteyshenkilo-matkapuhelin
                       :otsikko "Puhelinnumero"
                       :hae #(-> % ::t/tilaajayhteyshenkilo ::t/matkapuhelin)
                       :tyyppi :puhelin}
                      #_{:nimi :tienumero
                       :otsikko "Tienumero"
                       :tyyppi :positiivinen-numero
                       :muokattava? (constantly true)}
                      {:otsikko "Osoite"
                       :nimi ::t/osoite
                       :pakollinen? true
                       :tyyppi :tierekisteriosoite
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
                       })

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
        (lomake/ryhma "Työn tyyppi"
                      {:otsikko "Tienrakennustyöt"
                       :nimi :tyotyypit-a
                       :tyyppi :checkbox-group
                       :vaihtoehdot (map first tiedot/tyotyyppi-vaihtoehdot-tienrakennus)
                       :vaihtoehto-nayta tiedot/tyotyyppi-vaihtoehdot-map
                       :disabloi? (constantly false)}
                      {:otsikko "Huolto- ja ylläpitotyöt"
                       :nimi :tyotyypit-b
                       :tyyppi :checkbox-group
                       :vaihtoehdot (map first tiedot/tyotyyppi-vaihtoehdot-huolto)
                       :vaihtoehto-nayta tiedot/tyotyyppi-vaihtoehdot-map
                       :disabloi? (constantly false)}
                      {:otsikko "Asennustyöt"
                       :nimi :tyotyypit-c
                       :tyyppi :checkbox-group
                       :vaihtoehdot (map first tiedot/tyotyyppi-vaihtoehdot-asennus)
                       :vaihtoehto-nayta tiedot/tyotyyppi-vaihtoehdot-map
                       :disabloi? (constantly false)}
                      {:otsikko "Muut"
                       :nimi :tyotyypit-d
                       :tyyppi :checkbox-group-muu
                       :vaihtoehdot (map first tiedot/tyotyyppi-vaihtoehdot-muut)
                       :vaihtoehto-nayta tiedot/tyotyyppi-vaihtoehdot-map ;; -> muu
                       :muu-vaihtoehto "Muu, mikä?"
                       :muu-kentta {:otsikko "" :nimi :jotain :tyyppi :string :placeholder "(Muu tyyppi?)"}
                       :disabloi? (constantly false)}
                      )
        (lomake/ryhma "Työaika"
                      {:otsikko "Päivittäinen työaika"
                       :nimi :tyoaika
                       :tyyppi :string
                       :placeholder "(esim 8-16)"})
        (lomake/ryhma "Vaikutukset liikenteelle"
                      {:otsikko "Arvioitu viivytys normaalissa liikenteessä (min)"
                       :nimi ::t/viivastys-normaali-liikenteessa
                       :tyyppi :positiivinen-numero}
                      {:otsikko "Arvioitu viivytys ruuhka-aikana (min)"
                       :nimi ::t/viivastys-ruuhka-aikana
                       :tyyppi :positiivinen-numero
                       }
                      {:otsikko "Kaistajärjestelyt"
                       :tyyppi :checkbox-group-muu
                       :nimi :tietyon_kaistajarjestelyt
                       :vaihtoehdot (map first tiedot/kaistajarjestelyt-vaihtoehdot-map)
                       :vaihtoehto-nayta tiedot/kaistajarjestelyt-vaihtoehdot-map
                       :muu-vaihtoehto "Muu"
                       :muu-kentta {:otsikko "" :nimi :jotain :tyyppi :string :placeholder "(muu kaistajärjestely?)"}}


                      #_{:otsikko "Nopeusrajoitus 50 km/h, metriä"
                       :tyyppi :komponentti
                       :komponentti #(-> % :data ::t/nopeusrajoitukset nopeusrajoitukset-komponentti )
                       :nimi :nopeusrajoitukset
                       }
                      {:otsikko "Kulkurajoituksia"
                       :tyyppi :checkbox-group
                       :nimi :kulkurajoituksia
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
                       :nimi :kulkurajoituksia
                       :vaihtoehdot ["Liikennevalot" "Liikenteen ohjaaja" "Satunnaisia (aikataulu, jos yli 5 min)" "Aikataulu:"]}
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
        (lomake/ryhma "Muuta"
                      {:otsikko ""
                       :nimi :muuta
                       :tyyppi :string
                       })
        (lomake/ryhma "Ilmoittaja"
                      {:nimi :ilmoittaja-nimi
                       :otsikko "Nimi"
                       :hae #(-> %  ::t/urakoitsijayhteyshenkilo tiedot/henkilo->nimi)
                       :muokattava? (constantly true)
                       :tyyppi :string}
                      {:nimi :ilmoittaja-matkapuhelin
                       :otsikko "Puhelinnumero"
                       :hae #(-> % ::t/ilmoittaja ::t/matkapuhelin)
                       :tyyppi :puhelin}
                      {:otsikko "Päivämäärä"
                       :nimi :luotu
                       :tyyppi :string}
)]
       ilmoitus]]]))
