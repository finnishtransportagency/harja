(ns harja.ui.kartta.asioiden-tiedot
  "Määrittelee erilaisten kartalle piirrettävien asioiden tiedot, jotka tulevat kartta
  overlay näkymään."
  (:require [clojure.string :as str]))


(defmulti tiedot :tyyppi-kartalla)

(defmethod tiedot :tyokone [tyokone]
  {:otsikko "Työkone"
   :tiedot [{:otsikko "Työ aloitettu" :tyyppi :pvm-aika :nimi :alkanut}
            {:otsikko "Viimeisin havainto" :tyyppi :pvm-aika :nimi :lahetysaika}
            {:otsikko "Tyyppi" :tyyppi :string :nimi :tyokonetyyppi}
            {:otsikko "Organisaatio" :tyyppi :string :hae #(or (:organisaationimi %) "Ei organisaatiotietoja")}
            {:otsikko "Urakka" :tyyppi :string :hae #(or (:urakkanimi %) "Ei urakkatietoja")}
            {:otsikko "Tehtävät" :tyyppi :string
             :hae #(str/join ", " (:tehtavat %))}
            {:otsikko "Työkoneen tiedot FIXME: huono esimerkki" :tyyppi :linkki
             :tapahtuma (merge {:aihe :tyokone-tiedot-linkki} tyokone)}]
   :data tyokone})

(defmethod tiedot :ilmoitus [ilmoitus]
  {:otsikko (condp = (:ilmoitustyyppi ilmoitus)
         :toimenpidepyynto "Toimenpidepyyntö"
         :tiedoitus "Tiedotus"
         (str/capitalize (name (:ilmoitustyyppi ilmoitus))))
   :tiedot [
            {:otsikko "Id" :tyyppi :string :nimi :ilmoitusid}
            {:otsikko "Ilmoitettu" :tyyppi :pvm-aika :nimi :ilmoitettu}
            {:otsikko "Otsikko" :tyyppi :string :nimi :otsikko}
            {:otsikko "Paikan kuvaus" :tyyppi :string :nimi :paikankuvaus}
            {:otsikko "Lisätietoja" :tyyppi :string :nimi :lisatieto}
            {:otsikko "Kuittaukset" :tyyppi :positiivinen-numero
             :hae #(count (:kuittaukset ilmoitus))}]
   :data ilmoitus})
