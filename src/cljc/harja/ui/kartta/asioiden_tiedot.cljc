(ns harja.ui.kartta.asioiden-tiedot
  "Määrittelee erilaisten kartalle piirrettävien asioiden tiedot, jotka tulevat kartta
  overlay näkymään.")


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
