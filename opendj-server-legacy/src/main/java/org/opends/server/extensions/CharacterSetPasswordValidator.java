/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.CharacterSetPasswordValidatorCfg;
import org.forgerock.opendj.server.config.server.PasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;

/**
 * This class provides an OpenDJ password validator that may be used to ensure
 * that proposed passwords contain at least a specified number of characters
 * from one or more user-defined character sets.
 */
public class CharacterSetPasswordValidator
       extends PasswordValidator<CharacterSetPasswordValidatorCfg>
       implements ConfigurationChangeListener<CharacterSetPasswordValidatorCfg>
{
  /** The current configuration for this password validator. */
  private CharacterSetPasswordValidatorCfg currentConfig;

  /** A mapping between the character sets and the minimum number of characters required for each. */
  private HashMap<String,Integer> characterSets;

  /**
   * A mapping between the character ranges and the minimum number of characters
   * required for each.
   */
  private HashMap<String,Integer> characterRanges;

  /** Creates a new instance of this character set password validator. */
  public CharacterSetPasswordValidator()
  {
    super();

    // No implementation is required here.  All initialization should be
    // performed in the initializePasswordValidator() method.
  }

  @Override
  public void initializePasswordValidator(
                   CharacterSetPasswordValidatorCfg configuration)
         throws ConfigException
  {
    configuration.addCharacterSetChangeListener(this);
    currentConfig = configuration;

    // Make sure that each of the character set and range definitions are
    // acceptable.
    processCharacterSetsAndRanges(configuration, true);
  }

  @Override
  public void finalizePasswordValidator()
  {
    currentConfig.removeCharacterSetChangeListener(this);
  }

  @Override
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      LocalizableMessageBuilder invalidReason)
  {
    // Get a handle to the current configuration.
    CharacterSetPasswordValidatorCfg config = currentConfig;
    HashMap<String,Integer> characterSets = this.characterSets;

    // Process the provided password.
    String password = newPassword.toString();
    HashMap<String,Integer> setCounts = new HashMap<>();
    HashMap<String,Integer> rangeCounts = new HashMap<>();
    for (int i=0; i < password.length(); i++)
    {
      char c = password.charAt(i);
      boolean found = false;
      for (String characterSet : characterSets.keySet())
      {
        if (characterSet.indexOf(c) >= 0)
        {
          Integer count = setCounts.get(characterSet);
          if (count == null)
          {
            setCounts.put(characterSet, 1);
          }
          else
          {
            setCounts.put(characterSet, count+1);
          }

          found = true;
          break;
        }
      }
      if (!found)
      {
        for (String characterRange : characterRanges.keySet())
        {
          int rangeStart = 0;
          while (rangeStart < characterRange.length())
          {
            if (characterRange.charAt(rangeStart) <= c
                && c <= characterRange.charAt(rangeStart+2))
            {
              Integer count = rangeCounts.get(characterRange);
              if (count == null)
              {
                rangeCounts.put(characterRange, 1);
              }
              else
              {
                rangeCounts.put(characterRange, count+1);
              }

              found = true;
              break;
            }
            rangeStart += 3;
          }
        }
      }
      if (!found && !config.isAllowUnclassifiedCharacters())
      {
        invalidReason.append(ERR_CHARSET_VALIDATOR_ILLEGAL_CHARACTER.get(c));
        return false;
      }
    }

    int usedOptionalCharacterSets = 0;
    int optionalCharacterSets = 0;
    int mandatoryCharacterSets = 0;
    for (Map.Entry<String, Integer> entry : characterSets.entrySet())
    {
      String characterSet = entry.getKey();
      int minimumCount = entry.getValue();
      Integer passwordCount = setCounts.get(characterSet);
      if (minimumCount > 0)
      {
        // Mandatory character set.
        mandatoryCharacterSets++;
        if (passwordCount == null || passwordCount < minimumCount)
        {
          invalidReason
              .append(ERR_CHARSET_VALIDATOR_TOO_FEW_CHARS_FROM_SET
                  .get(characterSet, minimumCount));
          return false;
        }
      }
      else
      {
        // Optional character set.
        optionalCharacterSets++;
        if (passwordCount != null)
        {
          usedOptionalCharacterSets++;
        }
      }
    }
    for (Map.Entry<String, Integer> entry : characterRanges.entrySet())
    {
      String characterRange = entry.getKey();
      int minimumCount = entry.getValue();
      Integer passwordCount = rangeCounts.get(characterRange);
      if (minimumCount > 0)
      {
        // Mandatory character set.
        mandatoryCharacterSets++;
        if (passwordCount == null || passwordCount < minimumCount)
        {
          invalidReason
              .append(ERR_CHARSET_VALIDATOR_TOO_FEW_CHARS_FROM_RANGE
                  .get(characterRange, minimumCount));
          return false;
        }
      }
      else
      {
        // Optional character set.
        optionalCharacterSets++;
        if (passwordCount != null)
        {
          usedOptionalCharacterSets++;
        }
      }
    }

    // Check minimum optional character sets are present.
    if (optionalCharacterSets > 0)
    {
      int requiredOptionalCharacterSets;
      if (currentConfig.getMinCharacterSets() == null)
      {
        requiredOptionalCharacterSets = 0;
      }
      else
      {
        requiredOptionalCharacterSets = currentConfig
            .getMinCharacterSets() - mandatoryCharacterSets;
      }

      if (usedOptionalCharacterSets < requiredOptionalCharacterSets)
      {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : characterSets.entrySet())
        {
          String characterSet = entry.getKey();
          if (entry.getValue() == 0)
          {
            if (builder.length() > 0)
            {
              builder.append(", ");
            }
            builder.append('\'');
            builder.append(characterSet);
            builder.append('\'');
          }
        }
        for (Map.Entry<String, Integer> entry : characterRanges.entrySet())
        {
          String characterRange = entry.getKey();
          if (entry.getValue() == 0)
          {
            if (builder.length() > 0)
            {
              builder.append(", ");
            }
            builder.append('\'');
            builder.append(characterRange);
            builder.append('\'');
          }
        }

        invalidReason.append(
            ERR_CHARSET_VALIDATOR_TOO_FEW_OPTIONAL_CHAR_SETS.get(
                requiredOptionalCharacterSets, builder));
        return false;
      }
    }

    // If we've gotten here, then the password is acceptable.
    return true;
  }

  /**
   * Parses the provided configuration and extracts the character set
   * definitions and associated minimum counts from them.
   *
   * @param  configuration  the configuration for this password validator.
   * @param  apply <CODE>true</CODE> if the configuration is being applied,
   *         <CODE>false</CODE> if it is just being validated.
   * @throws  ConfigException  If any of the character set definitions cannot be
   *                           parsed, or if there are any characters present in
   *                           multiple sets.
   */
  private void processCharacterSetsAndRanges(
                    CharacterSetPasswordValidatorCfg configuration,
                    boolean apply)
          throws ConfigException
  {
    HashMap<String,Integer> characterSets   = new HashMap<>();
    HashMap<String,Integer> characterRanges = new HashMap<>();
    HashSet<Character>      usedCharacters  = new HashSet<>();
    int mandatoryCharacterSets = 0;

    for (String definition : configuration.getCharacterSet())
    {
      int colonPos = definition.indexOf(':');
      if (colonPos <= 0)
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_NO_SET_COLON.get(definition);
        throw new ConfigException(message);
      }
      else if (colonPos == (definition.length() - 1))
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_NO_SET_CHARS.get(definition);
        throw new ConfigException(message);
      }

      int minCount;
      try
      {
        minCount = Integer.parseInt(definition.substring(0, colonPos));
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_INVALID_SET_COUNT
            .get(definition);
        throw new ConfigException(message);
      }

      if (minCount < 0)
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_INVALID_SET_COUNT
            .get(definition);
        throw new ConfigException(message);
      }

      String characterSet = definition.substring(colonPos+1);
      for (int i=0; i < characterSet.length(); i++)
      {
        char c = characterSet.charAt(i);
        if (usedCharacters.contains(c))
        {
          throw new ConfigException(ERR_CHARSET_VALIDATOR_DUPLICATE_CHAR.get(definition, c));
        }

        usedCharacters.add(c);
      }

      characterSets.put(characterSet, minCount);

      if (minCount > 0)
      {
        mandatoryCharacterSets++;
      }
    }

    // Check the ranges
    for (String definition : configuration.getCharacterSetRanges())
    {
      int colonPos = definition.indexOf(':');
      if (colonPos <= 0)
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_NO_RANGE_COLON.get(definition);
        throw new ConfigException(message);
      }
      else if (colonPos == (definition.length() - 1))
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_NO_RANGE_CHARS.get(definition);
        throw new ConfigException(message);
      }

      int minCount;
      try
      {
        minCount = Integer.parseInt(definition.substring(0, colonPos));
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_INVALID_RANGE_COUNT
            .get(definition);
        throw new ConfigException(message);
      }

      if (minCount < 0)
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_INVALID_RANGE_COUNT
            .get(definition);
        throw new ConfigException(message);
      }

      String characterRange = definition.substring(colonPos+1);
      /*
       * Ensure we have a number of valid range specifications which are
       * each 3 chars long.
       * e.g. "a-zA-Z0-9"
       */
      int rangeOffset = 0;
      while (rangeOffset < characterRange.length())
      {
        if (rangeOffset > characterRange.length() - 3)
        {
          LocalizableMessage message = ERR_CHARSET_VALIDATOR_SHORT_RANGE
              .get(definition, characterRange.substring(rangeOffset));
          throw new ConfigException(message);
        }

        if (characterRange.charAt(rangeOffset+1) != '-')
        {
          LocalizableMessage message = ERR_CHARSET_VALIDATOR_MALFORMED_RANGE
              .get(definition, characterRange
                  .substring(rangeOffset,rangeOffset+3));
          throw new ConfigException(message);
        }

        if (characterRange.charAt(rangeOffset) >=
            characterRange.charAt(rangeOffset+2))
        {
          LocalizableMessage message = ERR_CHARSET_VALIDATOR_UNSORTED_RANGE
              .get(definition, characterRange
                  .substring(rangeOffset, rangeOffset+3));
          throw new ConfigException(message);
        }

        rangeOffset += 3;
      }

      characterRanges.put(characterRange, minCount);

      if (minCount > 0)
      {
        mandatoryCharacterSets++;
      }
    }

    // Validate min-character-sets if necessary.
    int optionalCharacterSets = characterSets.size() + characterRanges.size()
        - mandatoryCharacterSets;
    if (optionalCharacterSets > 0
        && configuration.getMinCharacterSets() != null)
    {
      int minCharacterSets = configuration.getMinCharacterSets();

      if (minCharacterSets < mandatoryCharacterSets)
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_MIN_CHAR_SETS_TOO_SMALL
            .get(minCharacterSets);
        throw new ConfigException(message);
      }

      if (minCharacterSets > characterSets.size() + characterRanges.size())
      {
        LocalizableMessage message = ERR_CHARSET_VALIDATOR_MIN_CHAR_SETS_TOO_BIG
            .get(minCharacterSets);
        throw new ConfigException(message);
      }
    }

    if (apply)
    {
      this.characterSets = characterSets;
      this.characterRanges = characterRanges;
    }
  }

  @Override
  public boolean isConfigurationAcceptable(PasswordValidatorCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    CharacterSetPasswordValidatorCfg config =
         (CharacterSetPasswordValidatorCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      CharacterSetPasswordValidatorCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // Make sure that we can process the defined character sets.  If so, then
    // we'll accept the new configuration.
    try
    {
      processCharacterSetsAndRanges(configuration, false);
    }
    catch (ConfigException ce)
    {
      unacceptableReasons.add(ce.getMessageObject());
      return false;
    }

    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                      CharacterSetPasswordValidatorCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Make sure that we can process the defined character sets.  If so, then
    // activate the new configuration.
    try
    {
      processCharacterSetsAndRanges(configuration, true);
      currentConfig = configuration;
    }
    catch (Exception e)
    {
      ccr.setResultCode(DirectoryConfig.getServerErrorResultCode());
      ccr.addMessage(getExceptionMessage(e));
    }

    return ccr;
  }
}
