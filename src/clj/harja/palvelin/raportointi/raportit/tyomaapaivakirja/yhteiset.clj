(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset
  "Työmaapäiväkirja raportin yhteiset funktiot")

(defn body-teksti [teksti]
  [:varillinen-teksti
   {:kustomi-tyyli "body-text"
    :arvo teksti}])

(defn osion-otsikko [otsikko]
  [:otsikko-heading otsikko {:padding-top "50px"}])

(defn placeholder-ei-tietoja [teksti]
  [:varillinen-teksti
   {:kustomi-tyyli "ei-tietoja"
    :arvo teksti}])

(defn taulukko [{:keys [gridin-otsikko rivin-tiedot rivit oikealle-tasattavat]}]
  [:taulukko {:otsikko gridin-otsikko
              :oikealle-tasattavat-kentat (or oikealle-tasattavat #{})
              :tyhja "Ei Tietoja."
              :piilota-border? false
              :viimeinen-rivi-yhteenveto? false}
   rivin-tiedot rivit])