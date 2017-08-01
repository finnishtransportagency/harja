(ns harja.domain.laadunseuranta.laatupoikkeama
  (:require [harja.pvm :as pvm]))

(defn kuvaile-tekija [tekija]
  (case tekija
    :tilaaja "Tilaaja"
    :urakoitsija "Urakoitsija"
    :konsultti "Konsultti"
    ""))

(defn kuvaile-kasittelytapa [kasittelytapa]
  (case kasittelytapa
    :tyomaakokous "Työmaakokous"
    :puhelin "Puhelimitse"
    :kommentit "Harja-kommenttien perusteella"
    :muu "Muu tapa"
    nil))

(defn kuvaile-paatostyyppi [paatos]
  (case paatos
    :sanktio "Sanktio"
    :ei_sanktiota "Ei sanktiota"
    :hylatty "Hylätty"))

(defn kuvaile-paatos [{:keys [kasittelyaika paatos kasittelytapa]}]
  (when paatos
    (str
      (pvm/pvm kasittelyaika)
      " "
      (kuvaile-paatostyyppi paatos)
      " ("
      (kuvaile-kasittelytapa kasittelytapa) ")")))
