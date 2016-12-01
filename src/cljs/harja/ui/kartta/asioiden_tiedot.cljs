(ns harja.ui.kartta.asioiden-tiedot
  "Määrittelee erilaisten kartalle piirrettävien asioiden tiedot, jotka tulevat kartta
  overlay näkymään."
  (:require [clojure.string :as string]
            [harja.pvm :as pvm]
            [harja.loki :as log]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.turvallisuuspoikkeamat :as turpodomain]
            [harja.domain.laadunseuranta.tarkastukset :as tarkastukset]))

(defmulti kenttaskeema :tyyppi-kartalla)

(defmethod kenttaskeema :tyokone [tyokone]
  {:tyyppi :tyokone
   :otsikko "Työkone"
   :tiedot [{:otsikko "Työ aloitettu" :tyyppi :pvm-aika :nimi :alkanut}
            {:otsikko "Viimeisin havainto" :tyyppi :pvm-aika :nimi :lahetysaika}
            {:otsikko "Tyyppi" :tyyppi :string :nimi :tyokonetyyppi}
            {:otsikko "Organisaatio" :tyyppi :string :hae #(or (:organisaationimi %) "Ei organisaatiotietoja")}
            {:otsikko "Urakka" :tyyppi :string :hae #(or (:urakkanimi %) "Ei urakkatietoja")}
            {:otsikko "Tehtävät" :tyyppi :string
             :hae #(string/join ", " (:tehtavat %))}]
   :data tyokone})

(defmethod kenttaskeema :ilmoitus [ilmoitus]
  {:tyyppi :ilmoitus
   :otsikko (condp = (:ilmoitustyyppi ilmoitus)
              :toimenpidepyynto "Toimenpidepyyntö"
              :tiedoitus "Tiedotus"
              (string/capitalize (name (:ilmoitustyyppi ilmoitus))))
   :tiedot [
            {:otsikko "Id" :tyyppi :string :nimi :ilmoitusid}
            {:otsikko "Ilmoitettu" :tyyppi :pvm-aika :nimi :ilmoitettu}
            {:otsikko "Otsikko" :tyyppi :string :nimi :otsikko}
            {:otsikko "Paikan kuvaus" :tyyppi :string :nimi :paikankuvaus}
            {:otsikko "Lisätietoja" :tyyppi :string :nimi :lisatieto}
            {:otsikko "Kuittaukset" :tyyppi :positiivinen-numero
             :hae #(count (:kuittaukset ilmoitus))}]
   :data ilmoitus})

(defmethod kenttaskeema :varustetoteuma [toteuma]
  {:tyyppi :varustetoteuma
   :otsikko "Varustetoteuma"
   :tiedot [{:otsikko "Päivämäärä" :tyyppi :pvm :nimi :alkupvm}
            {:otsikko "Tunniste" :tyyppi :string :nimi :tunniste}
            {:otsikko "Tietolaji" :tyyppi :string :nimi :tietolaji}
            {:otsikko "Toimenpide" :tyyppi :string :nimi :toimenpide}
            {:otsikko "Kuntoluokka" :tyyppi :string :nimi :kuntoluokka}
            {:otsikko "Avaa varustekortti" :tyyppi :linkki :nimi :varustekortti-url}]
   :data toteuma})

(defmethod kenttaskeema :paikkaus [paikkaus]
  (let [aloitus :aloituspvm
        paikkaus-valmis :paikkausvalmispvm
        kohde-valmis :kohdevalmispvm]
    {:tyyppi  :paikkaus
    :otsikko "Paikkauskohde"
    :tiedot  [{:otsikko "Nimi" :tyyppi :string :hae #(get-in % [:kohde :nimi])}
              {:otsikko "Tie\u00ADrekisteri\u00ADkohde" :tyyppi :string :hae #(get-in % [:kohdeosa :nimi])}
              {:osoite "Osoite" :tyyppi :tierekisteriosoite :nimi :tr}
              {:otsikko "Nykyinen päällyste" :tyyppi :string
               :hae     #(paallystys-ja-paikkaus/hae-paallyste-koodilla (:nykyinen-paallyste %))}
              {:otsikko "Toimenpide" :tyyppi :string :nimi :toimenpide}
              {:otsikko "Tila" :tyyppi :string
               :hae #(yllapitokohteet/kuvaile-kohteen-tila (get-in % [:paikkausilmoitus :tila]))}
              (when (aloitus paikkaus)
                {:otsikko "Aloitettu" :tyyppi :pvm-aika :nimi aloitus})
              (when (paikkaus-valmis paikkaus)
                {:otsikko "Paikkaus valmistunut" :tyyppi :pvm-aika :nimi paikkaus-valmis})
              (when (kohde-valmis paikkaus)
                {:otsikko "Kohde valmistunut" :tyyppi :pvm-aika :nimi kohde-valmis})]
     :data paikkaus}))

(defmethod kenttaskeema :paallystys [paallystys]
  (let [aloitus :aloituspvm
        paallystys-valmis :paallystysvalmispvm
        kohde-valmis :kohdevalmispvm]
    {:tyyppi :paallystys
     :otsikko "Päällystyskohde"
     :tiedot [{:otsikko "Nimi" :tyyppi :string :hae #(get-in % [:kohde :nimi])}
              {:otsikko "Tie\u00ADrekisteri\u00ADkohde" :tyyppi :string
               :hae #(get-in % [:kohdeosa :nimi])}
              {:otsikko "Osoite" :tyyppi :tierekisteriosoite :nimi :tr}
              {:otsikko "Nykyinen päällyste" :tyyppi :string
               :hae #(paallystys-ja-paikkaus/hae-paallyste-koodilla (:nykyinen-paallyste %))}
              {:otsikko "Toimenpide" :tyyppi :string :nimi :toimenpide}
              {:otsikko "Tila" :tyyppi :string
               :hae #(yllapitokohteet/kuvaile-kohteen-tila (get-in % [:paallystysilmoitus :tila]))}
              (when (aloitus paallystys)
                {:otsikko "Aloitettu" :tyyppi :pvm-aika :nimi aloitus})
              (when (paallystys-valmis paallystys)
                {:otsikko "Päällystys valmistunut" :tyyppi :pvm-aika :nimi paallystys-valmis})
              (when (kohde-valmis paallystys)
                {:otsikko "Kohde valmistunut" :tyyppi :pvm-aika :nimi kohde-valmis})]
     :data paallystys}))

(defmethod kenttaskeema :turvallisuspoikkeama [turpo]
  (let [tapahtunut :tapahtunut
        paattynyt :paattynyt
        kasitelty :kasitelty]
    {:tyyppi  :turvallisuuspoikkeama
     :otsikko "Turvallisuuspoikkeama"
     :tiedot  [(when (tapahtunut turpo)
                 {:otsikko "Tapahtunut" :tyyppi :pvm-aika :nimi tapahtunut})
               (when (kasitelty turpo)
                 {:otsikko "Käsitelty" :tyyppi :pvm-aika :nimi kasitelty})
               {:otsikko "Työn\u00ADtekijä" :hae #(turpodomain/kuvaile-tyontekijan-ammatti %)}
               {:otsikko "Vammat" :hae #(string/join ", " (map turpodomain/vammat (:vammat %)))}
               {:otsikko "Sairaala\u00ADvuorokaudet" :hae #(:sairaalavuorokaudet %)}
               {:otsikko "Sairaus\u00ADpoissaolo\u00ADpäivät" :tyyppi :positiivinen-numero :nimi :sairauspoissaolopaivat}
               {:otsikko "Vakavuus\u00ADaste" :hae #(turpodomain/turpo-vakavuusasteet (:vakavuusaste %))}
               {:otsikko "Kuvaus" :nimi :kuvaus}
               {:otsikko "Korjaavat toimen\u00ADpiteet"
                :hae #(str (count (filter :suoritettu (:korjaavattoimenpiteet %)))
                           "/"
                           (count (:korjaavattoimenpiteet %)))}]
     :data    turpo}))

(defmethod kenttaskeema :tarkastus [tarkastus]
  (let [havainnot-fn #(cond
                        (and (:havainnot %) (not-empty (:vakiohavainnot %)))
                        (str (:havainnot %) " & " (string/join ", " (:vakiohavainnot %)))

                        (:havainnot %)
                        (:havainnot %)

                        (not-empty (:vakiohavainnot %))
                        (string/join ", " (:vakiohavainnot %))

                        :default nil)]
    {:tyyppi  :tarkastus
     :otsikko (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus))
     :tiedot  [{:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
               {:otsikko "Tarkastaja" :nimi :tarkastaja}
               {:otsikko "Havainnot" :hae havainnot-fn}]
     :data    tarkastus}))

(defmethod kenttaskeema :laatupoikkeama [laatupoikkeama]
  (let [paatos #(get-in % [:paatos :paatos])
        kasittelyaika #(get-in % [:paatos :kasittelyaika])]
    {:tyyppi  :laatupoikkeama
     :otsikko "Laatupoikkeama"
     :tiedot  [{:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
               {:otsikko "Tekijä" :hae #(str (:tekijanimi %) ", " (name (:tekija %)))}
               (when (and (paatos laatupoikkeama) (kasittelyaika laatupoikkeama))
                 {:otsikko "Päätös"
                  :hae     #(str (laatupoikkeamat/kuvaile-paatostyyppi (paatos %))
                                 " (" (pvm/pvm-aika (kasittelyaika %)) ")")})]
     :data    laatupoikkeama}))

(defmethod kenttaskeema :suljettu-tieosuus [osuus]
  {:tyyppi :suljettu-tieosuus
   :otsikko "Suljettu tieosuus"
   :tiedot [{:otsikko "Ylläpitokohde" :hae #(str (:yllapitokohteen-nimi %) " (" (:yllapitokohteen-numero %) ")")}
            {:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
            {:otsikko "Osoite" :tyyppi :tierekisteriosoite :nimi :tr}
            {:otsikko "Kaistat" :hae #(string/join ", " (map str (:kaistat %)))}
            {:otsikko "Ajoradat" :hae #(string/join ", " (map str (:ajoradat %)))}]
   :data osuus})

(defmethod kenttaskeema :toteuma [toteuma]
  {:tyyppi :toteuma
   :otsikko "Toteuma"
   :tiedot [{:otsikko "Alkanut" :tyyppi :pvm-aika :nimi :alkanut}
            {:otsikko "Päättynyt" :tyyppi :pvm-aika :nimi :paattynyt}
            {:otsikko "Suorittaja" :hae #(get-in % [:suorittaja :nimi])}
            (for [tehtava (:tehtavat toteuma)]
              {:otsikko (:toimenpide tehtava) :hae #(str (get-in % [:tehtavat tehtava :maara]) " "
                                                         (get-in % [:tehtavat tehtava :yksikko]))})
            (for [materiaalitoteuma (:materiaalit toteuma)]
              {:otsikko (get-in materiaalitoteuma [:materiaali :nimi])
               :hae #(str (get-in % [:materiaalit materiaalitoteuma :maara]) " "
                          (get-in % [:materiaalit materiaalitoteuma :materiaali :yksikko]))})
            (when (:lisatieto toteuma)
              {:otsikko "Lisätieto" :nimi :lisatieto})]
   :data toteuma})

(defn validoi-tieto [tieto]
  (let [otsikko (:otsikko tieto)
        skeema (remove empty? (:tiedot tieto))
        data (:data tieto)
        kenttien-arvot (map
                         (fn [rivin-skeema]
                           [rivin-skeema
                            ((or (:nimi rivin-skeema) (:hae rivin-skeema)) data)])
                         skeema)
        tyhjat-arvot (map (comp :otsikko first) (filter (comp nil? second) kenttien-arvot))]
    (when-not (empty? tyhjat-arvot)
      (log/error "Yritettiin muodostaa overlayn tietoja asialle " otsikko ", mutta seuraavat tiedot puuttuivat: " (pr-str tyhjat-arvot)))
    (assoc tieto :tiedot (mapv first (remove (comp nil? second) kenttien-arvot)))))

(defn validoi-tiedot [tiedot]
  (map validoi-tieto tiedot))

(defn asioiden-pisteessa-skeemamuoto [data]
  (validoi-tiedot (map kenttaskeema data)))
