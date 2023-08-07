(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset
  "Työmaapäiväkirja raportin yhteiset funktiot")

(defn body-teksti [teksti]
  [:varillinen-teksti
   {:arvo teksti
    :font-size "8pt"
    :kustomi-tyyli "body-text"}])

(defn osion-otsikko [otsikko]
  [:otsikko-heading otsikko {:padding-top "50px"}])

(defn placeholder-ei-tietoja [teksti]
  [:varillinen-teksti
   {:arvo teksti
    :himmenna? true
    :font-size "8pt"
    :kustomi-tyyli "ei-tietoja"}])

(defn taulukko [{:keys [gridin-otsikko rivin-tiedot rivit oikealle-tasattavat]}]
  [:taulukko {:otsikko gridin-otsikko
              :oikealle-tasattavat-kentat (or oikealle-tasattavat #{})
              :tyhja "Ei Tietoja."
              :piilota-border? false
              :viimeinen-rivi-yhteenveto? false}
   rivin-tiedot rivit])
